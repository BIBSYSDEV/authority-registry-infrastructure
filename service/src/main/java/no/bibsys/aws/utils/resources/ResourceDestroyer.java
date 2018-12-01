package no.bibsys.aws.utils.resources;

import com.amazonaws.services.apigateway.AmazonApiGateway;
import com.amazonaws.services.apigateway.AmazonApiGatewayClientBuilder;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;
import no.bibsys.aws.cloudformation.Stage;
import no.bibsys.aws.git.github.GitInfo;
import no.bibsys.aws.lambda.deploy.handlers.SwaggerHubUpdater;
import no.bibsys.aws.route53.Route53Updater;
import no.bibsys.aws.route53.StaticUrlInfo;
import no.bibsys.aws.swaggerhub.SwaggerHubInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Opposite functionality of the ResourceInitializer. It is called before the deletion of a
 * CloudFormation Stack in order to disconnect the Stack with resources that do not belong to the
 * stack (either inside or outside AWS). It also deletes the disconnected resources. It is usually
 * called thought a handler of a Lambda function (see {@link no.bibsys.aws.lambda.deploy.handlers.DestroyHandler}).
 * <p>Currently it deletes the API from SwaggerHub and all Route53 and ApiGateway configurations
 * related to attaching the
 * branch's RestApi to a static url.
 * </p>
 */

public class ResourceDestroyer extends ResourceManager {

    private static final Logger logger = LoggerFactory.getLogger(ResourceDestroyer.class);


    private final transient SwaggerHubUpdater swaggerHubUpdater;
    private final transient Route53Updater route53Updater;

    public ResourceDestroyer(String stackName,
        StaticUrlInfo staticUrlInfo,
        SwaggerHubInfo swaggerHubInfo,
        Stage stage,
        GitInfo gitinfo)
        throws IOException {
        super();
        AmazonApiGateway client = AmazonApiGatewayClientBuilder.defaultClient();

        String apiGatewayRestApiId = findRestApi(stackName);

        swaggerHubUpdater = new SwaggerHubUpdater(client,
            apiGatewayRestApiId,
            swaggerHubInfo,
            stage,
            stackName,
            gitinfo);
        AmazonApiGateway apiGateway = AmazonApiGatewayClientBuilder.defaultClient();

        route53Updater = new Route53Updater(staticUrlInfo, apiGatewayRestApiId, apiGateway);
    }


    public void destroy() throws IOException, URISyntaxException {

        int response = swaggerHubUpdater.deleteApi();
        Optional<ChangeResourceRecordSetsRequest> request = this.route53Updater
            .createDeleteRequest();
        request.ifPresent(route53Updater::executeDeleteRequest);

        logger.debug("Swagger response" + response);


    }


}
