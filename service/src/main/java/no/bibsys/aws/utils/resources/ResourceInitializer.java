package no.bibsys.aws.utils.resources;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import no.bibsys.aws.cloudformation.Stage;
import no.bibsys.aws.lambda.deploy.handlers.SwaggerHubUpdater;
import no.bibsys.aws.lambda.responses.SimpleResponse;
import no.bibsys.aws.route53.Route53Updater;
import no.bibsys.aws.route53.StaticUrlInfo;
import no.bibsys.aws.swaggerhub.SwaggerHubInfo;
import no.bibsys.aws.utils.network.NetworkConstants;


/**
 * It is called after the creation of a CloudFormation Stack in order to create resources that do
 * not belong to the Stack. These resources can be inside or outside AWS. <br/> It is usually called
 * thought a handler of a Lambda function (see {@link no.bibsys.aws.lambda.deploy.handlers.InitHandler}).
 * <p>Currently it stores the API specification to SwaggerHub and creates all Route53 and
 * ApiGateway
 * <br/>configurations related to attaching the branch's RestApi to a static url.
 * </p>
 */
public class ResourceInitializer extends ResourceManager {

    private final transient SwaggerHubUpdater swaggerHubUpdater;
    private final transient Route53Updater route53Updater;
    private final transient String certificateArn;

    public ResourceInitializer(String zoneName, String stackId, SwaggerHubInfo swaggerHubInfo,
        Stage stage,
        String certificateArn) throws IOException {
        super();
        AmazonApiGateway apiGateway = AmazonApiGatewayClientBuilder.defaultClient();
        String apiGatewayRestApi = findRestApi(stackId);

        this.swaggerHubUpdater = new SwaggerHubUpdater(apiGateway, apiGatewayRestApi,
            swaggerHubInfo, stage);
        StaticUrlInfo staticUrlINfo = new StaticUrlInfo(zoneName, NetworkConstants.RECORD_SET_NAME,
            stage);
        this.route53Updater = new Route53Updater(staticUrlINfo, apiGatewayRestApi, apiGateway);
        this.certificateArn = certificateArn;
    }


    public SimpleResponse initializeStacks() throws IOException, URISyntaxException {

        System.out.println("Lambda function started");
        System.out.println("Updating Route 53");

        deletePreviousResources();

        Optional<ChangeResourceRecordSetsRequest> requestOpt = route53Updater
            .createUpdateRequest();

        Optional<ChangeResourceRecordSetsResult> route53UpdateResult = requestOpt.map(
            req -> route53Updater.executeUpdateRequest(req, certificateArn));

        String route53Status =
            route53UpdateResult.map(result -> result.getChangeInfo().getStatus())
                .orElse("Server not updated");

        StringBuilder output = new StringBuilder(20);
        output.append("Swagger:");

        Optional<String> swaggerUpdateResult = swaggerHubUpdater.updateApiDocumentation();
        swaggerUpdateResult.ifPresent(s -> output.append(s));
        output.append("\nRoute53:").append(route53Status);

        return new SimpleResponse(output.toString());

    }

    private void deletePreviousResources() throws URISyntaxException, IOException {
        Route53Updater testPhaseRoute53Updater = route53Updater.copy(Stage.TEST);
        Optional<ChangeResourceRecordSetsRequest> request = testPhaseRoute53Updater
            .createDeleteRequest();
        request.ifPresent(route53Updater::executeDeleteRequest);
        swaggerHubUpdater.deleteApi();
    }


}
