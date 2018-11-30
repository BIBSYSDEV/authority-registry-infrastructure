package no.bibsys.aws.lambda.deploy.handlers;

import static no.bibsys.aws.lambda.EnvironmentConstants.APPLICATION_URL;
import static no.bibsys.aws.lambda.EnvironmentConstants.STACK_NAME;
import static no.bibsys.aws.lambda.EnvironmentConstants.ZONE_NAME_ENV;

import com.amazonaws.services.lambda.runtime.Context;
import java.io.IOException;
import java.net.URISyntaxException;
import no.bibsys.aws.cloudformation.Stage;
import no.bibsys.aws.lambda.events.DeployEvent;
import no.bibsys.aws.lambda.handlers.templates.CodePipelineFunctionHandlerTemplate;
import no.bibsys.aws.lambda.responses.SimpleResponse;
import no.bibsys.aws.swaggerhub.SwaggerHubInfo;
import no.bibsys.aws.tools.Environment;
import no.bibsys.aws.utils.resources.ResourceDestroyer;

public class DestroyHandler extends CodePipelineFunctionHandlerTemplate<SimpleResponse> {


    private final transient Environment environment;


    public DestroyHandler() {
        this(new Environment());
    }


    public DestroyHandler(Environment environment) {
        super();
        this.environment = environment;

    }

    @Override
    protected SimpleResponse processInput(DeployEvent input, String apiGatewayInputString,
        Context context)
        throws IOException, URISyntaxException {
        Stage stage = Stage.currentStage();
        String zoneName = environment.readEnv(ZONE_NAME_ENV);
        String stackName = environment.readEnv(STACK_NAME);
        String applicationurl = environment.readEnv(APPLICATION_URL);

        SwaggerHubInfo swaggerHubInfo = new SwaggerHubInfo(environment);

        ResourceDestroyer resourceDestroyer = new ResourceDestroyer(zoneName, applicationurl,
            stackName, swaggerHubInfo, stage);
        resourceDestroyer.destroy();

        return new SimpleResponse("OK");

    }


}
