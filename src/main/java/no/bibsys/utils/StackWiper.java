package no.bibsys.utils;

import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvocationType;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ResourceNotFoundException;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.DeleteLogGroupRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListVersionsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.VersionListing;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Collectors;
import no.bibsys.cloudformation.PipelineStackConfiguration;
import no.bibsys.cloudformation.Stage;
import no.bibsys.lambda.deploy.handlers.SwaggerHubInfo;

public class StackWiper {

    private final transient PipelineStackConfiguration pipelineStackConfiguration;


    public StackWiper(PipelineStackConfiguration pipelineStackConfiguration) {
        this.pipelineStackConfiguration = pipelineStackConfiguration;



    }


    public void deleteStacks() {
        AmazonCloudFormation acf = AmazonCloudFormationClientBuilder.defaultClient();

        String stack = pipelineStackConfiguration.getPipelineConfiguration()
            .getTestServiceStack();

        acf.deleteStack(new DeleteStackRequest().withStackName(stack));
        awaitDeleteStack(acf, stack);

        stack = pipelineStackConfiguration.getPipelineConfiguration().getFinalServiceStack();
        acf.deleteStack(new DeleteStackRequest().withStackName(stack));
        awaitDeleteStack(acf, stack);

        stack = pipelineStackConfiguration.getPipelineStackName();

        acf.deleteStack(new DeleteStackRequest().withStackName(stack));
        awaitDeleteStack(acf, stack);

    }


    private Integer invokeDeleteLambdaFunction() {
        String destroyFunctionName = null;
        try {
            AWSLambda lambda = AWSLambdaClientBuilder.defaultClient();
            destroyFunctionName = pipelineStackConfiguration.getPipelineConfiguration()
                .getDestroyLambdaFunctionName();
            destroyFunctionName = String.join("-", destroyFunctionName, Stage.FINAL.toString());
            InvokeRequest request = new InvokeRequest();
            request.withInvocationType(InvocationType.RequestResponse)
                .withFunctionName(destroyFunctionName);
            InvokeResult invokeResult = lambda.invoke(request);
            return invokeResult.getStatusCode();
        } catch (ResourceNotFoundException e) {
            System.out
                .println(String.format("Function %s could not be found", destroyFunctionName));
        }

        return -1;

    }

    private void deleteBuckets() {
        deleteBucket(pipelineStackConfiguration.getBucketName());
    }


    private void deleteLogs() {
        AWSLogs logsClient = AWSLogsClientBuilder.defaultClient();
        List<String> logGroups = logsClient
            .describeLogGroups().getLogGroups().stream()
            .map(group -> group.getLogGroupName())
            .filter(name -> filterLogGroups(pipelineStackConfiguration, name))
            .collect(Collectors.toList());

        logGroups.stream()
            .map(group -> new DeleteLogGroupRequest().withLogGroupName(group))
            .forEach(request -> logsClient.deleteLogGroup(request));


    }


    public void wipeStacks(SwaggerHubInfo swaggerHubInfo, String networkZoneName)
        throws IOException, URISyntaxException {
        int invokeStatusCode = invokeDeleteLambdaFunction();
        System.out.println(invokeStatusCode);
        destroyResources(swaggerHubInfo, networkZoneName);
        //Delete buckets first because they cannot be deleted automatically when we delete a Stack
        deleteBuckets();
        deleteStacks();
        deleteLogs();
    }


    private void destroyResources(SwaggerHubInfo swaggerHubInfo, String netowrkZoneName)
        throws IOException, URISyntaxException {
        List<Stage> stages = Stage.listStages();
        String repository=pipelineStackConfiguration.getGithubConf().getRepo();
        String branch=pipelineStackConfiguration.getBranchName();


        for (Stage stage : stages) {

            ResourceDestroyer destroyer = new ResourceDestroyer(netowrkZoneName, repository, branch,
                swaggerHubInfo, stage);
            destroyer.destroy();
        }
    }


    private boolean filterLogGroups(PipelineStackConfiguration conf, String name) {
        boolean result = name.contains(conf.getProjectId())
            && name.contains(conf.getNormalizedBranchName());
        return result;
    }


    private void awaitDeleteStack(AmazonCloudFormation acf, String stackname) {

        int counter = 0;
        List<String> stackNames = acf.describeStacks().getStacks().stream()
            .map(stack -> stack.getStackName())
            .collect(Collectors.toList());

        while (stackNames.contains(stackname) && counter < 100) {
            stackNames = acf.describeStacks().getStacks().stream()
                .map(stack -> stack.getStackName())
                .collect(Collectors.toList());
            counter++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


        }

    }


    private void deleteBucket(String bucketName) {
        try {
            AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
            emptyBucket(bucketName, s3);
            s3.deleteBucket(bucketName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void emptyBucket(String bucketName, AmazonS3 s3) {
        VersionListing versionListing = s3
            .listVersions(new ListVersionsRequest().withBucketName(bucketName));

        while (versionListing.isTruncated()) {
            deleteVersionBatch(bucketName, s3, versionListing);
            versionListing = s3.listNextBatchOfVersions(versionListing);
        }
        deleteVersionBatch(bucketName, s3, versionListing);

        ObjectListing objectListing = s3.listObjects(bucketName);
        while (objectListing.isTruncated()) {
            deleteObjectBatch(bucketName, s3, objectListing);
            objectListing = s3.listNextBatchOfObjects(objectListing);
        }

        deleteObjectBatch(bucketName, s3, objectListing);

        // Be sure we have nothing more to delete
        if (!versionListing.getVersionSummaries().isEmpty()
            || !objectListing.getObjectSummaries().isEmpty()) {
            emptyBucket(bucketName, s3);
        }

    }


    private void deleteObjectBatch(String bucketName, AmazonS3 s3, ObjectListing objectListing) {
        objectListing.getObjectSummaries().stream()
            .forEach(object -> s3.deleteObject(bucketName, object.getKey()));
    }

    private void deleteVersionBatch(String bucketName, AmazonS3 s3, VersionListing versionListing) {
        versionListing.getVersionSummaries().forEach(version ->
            s3.deleteVersion(bucketName, version.getKey(), version.getVersionId()));
    }


}
