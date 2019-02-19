package no.bibsys.aws.lambda.api.handlers;

import static no.bibsys.aws.lambda.EnvironmentConstants.READ_FROM_GITHUB_SECRET_KEY;
import static no.bibsys.aws.lambda.EnvironmentConstants.READ_FROM_GITHUB_SECRET_NAME;
import static no.bibsys.aws.lambda.EnvironmentConstants.REST_USER_API_KEY_SECRET_KEY;
import static no.bibsys.aws.lambda.EnvironmentConstants.REST_USER_API_KEY_SECRET_NAME;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import no.bibsys.aws.lambda.api.requests.UpdateStackRequest;
import no.bibsys.aws.lambda.api.utils.Action;
import no.bibsys.aws.secrets.AWSSecretsReader;
import no.bibsys.aws.secrets.SecretsReader;
import no.bibsys.aws.tools.Environment;
import no.bibsys.aws.tools.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateStackRequestHandler extends ApiHandler {

    protected static final String API_KEY_HEADER = "api-key";
    private static final Logger logger = LoggerFactory.getLogger(UpdateStackRequest.class);
    private final SecretsReader readFromGithubSecretsReader;
    private transient SecretsReader restApiKeySecretsReader;

    public UpdateStackRequestHandler() {
        super(new Environment(),
            AmazonCloudFormationClientBuilder.defaultClient(),
            AmazonS3ClientBuilder.defaultClient(),
            AWSLambdaClientBuilder.defaultClient(),
            AWSLogsClientBuilder.defaultClient()
        );

        this.restApiKeySecretsReader = new AWSSecretsReader(
            environment.readEnv(REST_USER_API_KEY_SECRET_NAME)
            , environment.readEnv(REST_USER_API_KEY_SECRET_KEY),
            region);

        this.readFromGithubSecretsReader = new AWSSecretsReader(
            environment.readEnv(READ_FROM_GITHUB_SECRET_NAME),
            environment.readEnv(READ_FROM_GITHUB_SECRET_KEY),
            region);
    }

    public UpdateStackRequestHandler(Environment environment,
        AmazonCloudFormation acf,
        AmazonS3 s3,
        AWSLambda lambdaClient,
        AWSLogs logsClient,
        SecretsReader restApiKeySecretsReader,
        SecretsReader readFromGithubSecretsReader
    ) {

        super(environment, acf, s3, lambdaClient, logsClient);

        this.restApiKeySecretsReader = restApiKeySecretsReader;
        this.readFromGithubSecretsReader = readFromGithubSecretsReader;
    }

    @Override
    public String processInput(String string, Map<String, String> headers, Context context)
        throws IOException {

        String securityToken = headers.get(API_KEY_HEADER);
        checkAuthorization(securityToken);
        UpdateStackRequest request = parseRequest(string);

        if (request.getAction().equals(Action.CREATE)) {
            createStacks(request);
        }

        if (request.getAction().equals(Action.DELETE)) {
            deleteStacks(request);
        }

        logger.debug(request.toString());

        ObjectMapper objectMapper = JsonUtils.newJsonParser();
        return objectMapper.writeValueAsString(request);
    }

    private UpdateStackRequest parseRequest(String string) throws IOException {
        ObjectMapper mapper = JsonUtils.newJsonParser();
        return mapper.readValue(string, UpdateStackRequest.class);
    }

    private void checkAuthorization(String securityToken) throws IOException {

        String secret = restApiKeySecretsReader.readSecret();
        if (!secret.equals(securityToken)) {
            throw new UnauthorizedException("Wrong API key signature");
        }
    }

    @Override
    protected SecretsReader readFromGithubSecretReader() {
        return this.readFromGithubSecretsReader;
    }
}


