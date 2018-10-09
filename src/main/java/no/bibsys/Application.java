package no.bibsys;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.VersionListing;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import no.bibsys.cloudformation.PipelineStackConfiguration;
import no.bibsys.utils.IOUtils;

public class Application {


    Config config= ConfigFactory.load().resolve();


    private IOUtils ioUtils = new IOUtils();

    public static void main(String[] args) {
        Application application = new Application();
        application.pipeineStackConfiguration();
    }


    public PipelineStackConfiguration pipeineStackConfiguration() {
        String projectName = "emne-test";
        String branchName = config.getString("pipeline.branch");
        PipelineStackConfiguration pipelineStackConfiguration = new PipelineStackConfiguration(
            projectName, branchName);
        return pipelineStackConfiguration;

    }


    public CreateStackRequest createStackRequest(
        PipelineStackConfiguration pipelineStack) throws IOException {
        CreateStackRequest createStackRequest = new CreateStackRequest();
        createStackRequest.setStackName(pipelineStack.getPipelineStackName());
        List<Parameter> parameters = new ArrayList<>();
        parameters
            .add(newParameter("GithubAuth", pipelineStack.getGithubConf().getAuth()));
        parameters.add(
            newParameter("GithubOwner", pipelineStack.getGithubConf().getOwner()));
        parameters
            .add(newParameter("GithubRepo", pipelineStack.getGithubConf().getRepo()));

        parameters.add(newParameter("PipelineName",
            pipelineStack.getPipelineConfiguration().getPipelineName()));

        parameters.add(newParameter("PipelineBucketname", pipelineStack.getBucketName()));

        parameters.add(newParameter("PipelineRolename", pipelineStack.getPipelineRoleName()));
        parameters.add(newParameter("PipelineLambdaTrustRoleName", pipelineStack.getPipelineConfiguration().getLambdaTrustRolename()));
        parameters.add(newParameter("ProjectStage",pipelineStack.getStage()));

        parameters.add(newParameter("CreateStackRolename", pipelineStack.getCreateStackRoleName()));

        parameters.add(newParameter("SourceStageOutputArtifact", pipelineStack
            .getPipelineConfiguration().getSourceOutputArtifactName()));

        parameters.add(newParameter("ProjectId", pipelineStack.getProjectId()));
        parameters.add(newParameter("ProjectBranch", pipelineStack.getBranchName()));

        parameters.add(newParameter("CodebuildOutputArtifact",
            pipelineStack.getCodeBuildConfiguration().getOutputArtifact()));
        parameters.add(newParameter("CodebuildProjectname",
            pipelineStack.getCodeBuildConfiguration().getProjectName()));

        parameters.add(newParameter("TestStackName",
            pipelineStack.getPipelineConfiguration().getSystemStack()));

        createStackRequest.setParameters(parameters);
        createStackRequest.withCapabilities(Capability.CAPABILITY_NAMED_IAM);

        String templateBody = ioUtils
            .resourceAsString(Paths.get("templates", "pipelineTemplate.yaml"));
        createStackRequest.setTemplateBody(templateBody);

        return createStackRequest;

    }


    private Parameter newParameter(String key, String value) {
        return new Parameter().withParameterKey(key).withParameterValue(value);
    }

    public void deleteStacks(PipelineStackConfiguration pipelineStackConfiguration){
        AmazonCloudFormation acf= AmazonCloudFormationClientBuilder.defaultClient();

        String systemStack=pipelineStackConfiguration.getPipelineConfiguration().getSystemStack();
        DeleteStackRequest deleteStackRequest=new DeleteStackRequest()
            .withStackName(systemStack);

        acf.deleteStack(deleteStackRequest);

        String pipelineGenerationStack=pipelineStackConfiguration.getPipelineStackName();
        deleteBucket(pipelineStackConfiguration.getBucketName());
        deleteStackRequest=new DeleteStackRequest().withStackName(pipelineGenerationStack);
        acf.deleteStack(deleteStackRequest);

    }


    public void deleteBucket(String bucketName){
        try {
            AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
            emptyBucket(bucketName, s3);

            s3.deleteBucket(bucketName);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


    private void emptyBucket(String bucketName, AmazonS3 s3) {
        VersionListing versionListing= s3.listVersions(new ListVersionsRequest().withBucketName(bucketName));
        while(versionListing.isTruncated()){
            versionListing.getVersionSummaries().forEach(version ->
                s3.deleteVersion(bucketName, version.getKey(), version.getVersionId()));
            versionListing= s3.listNextBatchOfVersions(versionListing);
        }
        versionListing.getVersionSummaries().forEach(version ->
            s3.deleteVersion(bucketName, version.getKey(), version.getVersionId()));

        ObjectListing objectListing = s3.listObjects(bucketName);
        while(objectListing.isTruncated()){
            objectListing.getObjectSummaries().stream()
                .forEach(object -> s3.deleteObject(bucketName, object.getKey()));
            objectListing=s3.listNextBatchOfObjects(objectListing);
        }

        objectListing.getObjectSummaries().stream()
            .forEach(object -> s3.deleteObject(bucketName, object.getKey()));


        if(versionListing.isTruncated() || objectListing.isTruncated() ){
            emptyBucket(bucketName,s3);
        }

    }





}
