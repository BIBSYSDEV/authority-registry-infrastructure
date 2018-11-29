package no.bibsys.aws.lambda.api.handlers;


import com.amazonaws.services.apigateway.model.UnauthorizedException;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import no.bibsys.aws.lambda.api.requests.UpdateStackRequest;
import no.bibsys.aws.lambda.api.utils.Action;
import no.bibsys.aws.secrets.SecretsReader;
import no.bibsys.aws.tools.JsonUtils;

public class UpdateStackRequestHandler extends ApiHandler {

    private static final String AWS_SECRET_NAME = "infrastructure";
    private static final String AWS_SECRET_KEY = "buildbranch";


    private transient SecretsReader secretsReader;
    private static final String API_KEY_HEADER = "api-key";


    public UpdateStackRequestHandler() {
        super();

        this.secretsReader = new SecretsReader(AWS_SECRET_NAME, AWS_SECRET_KEY);
    }




    @Override
    public String processInput(String string, Map<String, String> headers, Context context) throws IOException {

        String securityToken = headers.get(API_KEY_HEADER);
        checkAuthorization(securityToken);
        UpdateStackRequest request = parseRequest(string);

        if (request.getAction().equals(Action.CREATE)) {
            createStacks(request);
        }

        if (request.getAction().equals(Action.DELETE)) {
            deleteStacks(request);
        }

        System.out.println(request.toString());

        ObjectMapper objectMapper = JsonUtils.newJsonParser();
        String requestJson = objectMapper.writeValueAsString(request);
        return requestJson;

    }

    private UpdateStackRequest parseRequest(String string) throws IOException {
        ObjectMapper mapper = JsonUtils.newJsonParser();
        return mapper.readValue(string, UpdateStackRequest.class);
    }

    private void checkAuthorization(String securityToken) throws IOException {

        String secret = secretsReader.readSecret();
        if (!secret.equals(securityToken)) {
            throw new UnauthorizedException("Wrong API key signature");
        }
    }

    public void setSecretsReader(SecretsReader secretsReader) {
        this.secretsReader = secretsReader;
    }



}


