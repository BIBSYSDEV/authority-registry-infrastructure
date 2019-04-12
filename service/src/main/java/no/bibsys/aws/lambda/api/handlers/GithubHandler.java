package no.bibsys.aws.lambda.api.handlers;

import static no.bibsys.aws.lambda.EnvironmentConstants.AWS_REGION;
import static no.bibsys.aws.lambda.EnvironmentConstants.GITHUB_WEBHOOK_SECRET_KEY;
import static no.bibsys.aws.lambda.EnvironmentConstants.GITHUB_WEBHOOK_SECRET_NAME;
import static no.bibsys.aws.lambda.EnvironmentConstants.READ_FROM_GITHUB_SECRET_KEY;
import static no.bibsys.aws.lambda.EnvironmentConstants.READ_FROM_GITHUB_SECRET_NAME;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.apigateway.model.UnauthorizedException;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import no.bibsys.aws.lambda.api.requests.GitEvent;
import no.bibsys.aws.lambda.api.requests.SimplePullRequest;
import no.bibsys.aws.secrets.AwsSecretsReader;
import no.bibsys.aws.secrets.GithubSignatureChecker;
import no.bibsys.aws.secrets.SecretsReader;
import no.bibsys.aws.tools.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubHandler extends ApiHandler {

    private static final Logger logger = LoggerFactory.getLogger(GithubHandler.class);
    private static final String GITHUB_SIGNATURE_HEADER = "X-Hub-Signature";
    private static final String ERROR_MESSAGE_FOR_FAILED_GITHUB_SIGNATURE = "Wrong API key signature";
    public static final String NO_ACTION_MESSAGE = "No action";

    private final transient SecretsReader readFromGithubSecretsReader;
    private final transient GithubSignatureChecker signatureChecker;

    private final transient SecretsReader webhookSecretsReader;

    /**
     * Used by AWS Lambda.
     */
    public GithubHandler() {
        super(new Environment(),
            AmazonCloudFormationClientBuilder.defaultClient(),
            AmazonS3ClientBuilder.defaultClient(),
            AWSLambdaClientBuilder.defaultClient(),
            AWSLogsClientBuilder.defaultClient()
        );

        String regionString = environment.readEnv(AWS_REGION);
        Region region = Region.getRegion(Regions.fromName(regionString));
        this.webhookSecretsReader = new AwsSecretsReader(
            environment.readEnv(GITHUB_WEBHOOK_SECRET_NAME),
            environment.readEnv(GITHUB_WEBHOOK_SECRET_KEY),
            region);
        this.readFromGithubSecretsReader = new AwsSecretsReader(
            environment.readEnv(READ_FROM_GITHUB_SECRET_NAME),
            environment.readEnv(READ_FROM_GITHUB_SECRET_KEY),
            region);
        this.signatureChecker = new GithubSignatureChecker(webhookSecretsReader);
    }

    public GithubHandler(Environment environment,
        AmazonCloudFormation acf,
        AmazonS3 s3,
        AWSLambda lambdaClient,
        AWSLogs logsClient,
        GithubSignatureChecker signatureChecker,
        SecretsReader webhookSecretsReader,
        SecretsReader readFromGithubSecretsReader
    ) {
        super(environment, acf, s3, lambdaClient, logsClient);
        this.signatureChecker = signatureChecker;
        this.webhookSecretsReader = webhookSecretsReader;
        this.readFromGithubSecretsReader = readFromGithubSecretsReader;
    }

    @Override
    public String processInput(String request, Map<String, String> headers, Context context)
        throws IOException {

        setRegionOrReportErrorToLogger();

        if (isVerified(request, headers)) {
            return processGitEvent(request);
        } else {
            throw new UnauthorizedException(ERROR_MESSAGE_FOR_FAILED_GITHUB_SIGNATURE);
        }
    }

    private void setRegionOrReportErrorToLogger() {
        init();
    }

    private boolean isVerified(String request, Map<String, String> headers) throws IOException {
        String webhookSecurityToken = headers.get(GITHUB_SIGNATURE_HEADER);
        return signatureChecker.verifySecurityToken(webhookSecurityToken, request);
    }

    private String processGitEvent(String request) throws IOException {
        Optional<GitEvent> gitEventOpt = parseEvent(request);
        String response = NO_ACTION_MESSAGE;
        if (gitEventOpt.isPresent()) {
            GitEvent event = gitEventOpt.get();
            if (event instanceof SimplePullRequest) {
                response = processPullRequest((SimplePullRequest) event);
            }
        }
        return response;
    }

    private String processPullRequest(SimplePullRequest simplePullRequest)
        throws IOException {
        if (simplePullRequest.getAction().equals(SimplePullRequest.ACTION_OPEN)
            || simplePullRequest.getAction().equals(SimplePullRequest.ACTION_REOPEN)) {
            createStacks(simplePullRequest);
        }

        if (simplePullRequest.getAction().equals(SimplePullRequest.ACTION_CLOSE)) {
            deleteStacks(simplePullRequest);
        }

        logger.info(simplePullRequest.toString());

        return simplePullRequest.toString();
    }

    private Optional<GitEvent> parseEvent(String json) throws IOException {
        return SimplePullRequest.create(json);
    }

    protected SecretsReader getWebhookSecretsReader() {
        return webhookSecretsReader;
    }

    @Override
    protected SecretsReader readFromGithubSecretReader() {
        return this.readFromGithubSecretsReader;
    }
}


