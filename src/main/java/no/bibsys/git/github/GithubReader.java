package no.bibsys.git.github;

import java.io.IOException;
import java.nio.file.Path;
import no.bibsys.utils.IoUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

public class GithubReader {

    private final transient IoUtils ioUtils=new IoUtils();
    private final transient GithubConf githubConf;
    private final transient static String urlTemplate="https://api.github.com/"
        + "repos/%1$s/%2$s/contents/template.yml?ref=%3$s&path=%4$s";


    private final transient String branch;

    public  GithubReader(GithubConf githubConf,String branch){
        this.githubConf=githubConf;
        this.branch=branch;

    }


    private String createUrl(Path path){
        String pathString=path.toString();
        return String.format(urlTemplate,
            githubConf.getOwner(),
            githubConf.getRepo(),
            branch,
            pathString
        );

    }


    public void readFile(Path path) throws IOException {
        CloseableHttpClient httpClient= HttpClients.createDefault();
        HttpGet get=new HttpGet(createUrl(path));
        get.setHeader(new BasicHeader("Authentication", githubConf.getOauth()));
        get.setHeader(new BasicHeader("Accept","application/vnd.github.v3+json"));
        CloseableHttpResponse response = httpClient.execute(get);
        HttpEntity responseEntity=response.getEntity();
        Preconditions.
        if(responseEntity!=null){

            String responseString=ioUtils.streamToString(responseEntity.getContent());

        }
        else {
            throw new IllegalStateException("Response HttpEntity was null");
        }



        response.close();

    }



}
