package no.bibsys.aws.utils.resources;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;

import no.bibsys.aws.cloudformation.Stage;
import no.bibsys.aws.git.github.GitInfo;
import no.bibsys.aws.lambda.deploy.handlers.SwaggerHubUpdater;
import no.bibsys.aws.lambda.responses.SimpleResponse;
import no.bibsys.aws.route53.Route53Updater;
import no.bibsys.aws.route53.StaticUrlInfo;

/**
 * It is called after the creation of a CloudFormation Stack in order to create resources that do
 * not belong to the Stack. These resources can be inside or outside AWS. <br/> It is usually called
 * thought a handler of a Lambda function (see {@link no.bibsys.aws.lambda.deploy.handlers.InitHandler}).
 * <p>Currently it stores the API specification to SwaggerHub and creates all Route53 and
 * ApiGateway <br/>configurations related to attaching the branch's RestApi to a static url.
 * </p>
 */
public class ResourceInitializer extends ResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ResourceInitializer.class);

    private final transient SwaggerHubUpdater swaggerHubUpdater;
    private final transient Route53Updater route53Updater;
    private final transient String certificateArn;

    public ResourceInitializer(String stackName,
        StaticUrlInfo staticUrlInfo,
        String certificateArn,
        SwaggerHubConnectionDetails swaggerHubConnectionDetails,
        Stage stage,
        GitInfo gitInfo,
        AmazonCloudFormation cloudFormationClient,
        AmazonApiGateway apiGatewayClient,
        AmazonRoute53 route53Client
    ) {
        super(cloudFormationClient);

        String apiGatewayRestApiId = findRestApi(stackName);

        this.swaggerHubUpdater = initSwaggerHubUpdater(stackName,
            swaggerHubConnectionDetails.getSwaggerHubInfo(), stage, gitInfo,
            apiGatewayClient,
            apiGatewayRestApiId, swaggerHubConnectionDetails.getSwaggerHubSecretsReader());

        StaticUrlInfo newStaticUrlInfo = initStaticUrlInfo(staticUrlInfo, gitInfo.getBranch());

        route53Updater = new Route53Updater(newStaticUrlInfo, apiGatewayRestApiId, apiGatewayClient,
            route53Client);
        this.certificateArn = certificateArn;
    }

    public SimpleResponse initializeStacks() throws IOException, URISyntaxException {

        logger.debug("Lambda function started");
        logger.debug("Updating Route 53");
        System.out.println("Lambda function started");
        System.out.println("Updating Route 53");

        deletePreviousResources();

        Optional<ChangeResourceRecordSetsRequest> requestOpt = route53Updater
            .createUpdateRequestForRecordSets(certificateArn);

        Optional<ChangeResourceRecordSetsResult> route53UpdateResult = requestOpt
            .map(route53Updater::executeUpdateRequest);

        String route53Status = route53UpdateResult.map(result -> result.getChangeInfo().getStatus())
            .orElse("Server not updated");

        StringBuilder output = new StringBuilder("Swagger:");
        Optional<String> swaggerUpdateResult = swaggerHubUpdater.updateApiDocumentation();
        swaggerUpdateResult.ifPresent(output::append);
        output.append("\nRoute53:").append(route53Status);
        logger.info(output.toString());
        return new SimpleResponse(output.toString());
    }

    private void deletePreviousResources() throws URISyntaxException, IOException {
        Route53Updater testPhaseRoute53Updater = route53Updater.copy(Stage.TEST);
        Optional<ChangeResourceRecordSetsRequest> request = testPhaseRoute53Updater
            .createDeleteRequest();
        request.ifPresent(route53Updater::executeDeleteRequest);
        swaggerHubUpdater.deleteApiVersion();
    }
}
