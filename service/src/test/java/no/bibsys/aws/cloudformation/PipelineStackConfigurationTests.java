package no.bibsys.aws.cloudformation;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import java.io.IOException;
import java.util.regex.Matcher;
import org.junit.Test;

public class PipelineStackConfigurationTests extends ConfigurationTests {


    public PipelineStackConfigurationTests() throws IOException {}

    @Test
    public void initPipelineStackName_projectIdAndNormalizedBranch_containsProjectId() {
        assertThat(conf.getPipelineStackName(), containsString(projectId));
    }

    @Test
    public void initPipelineStackName_projectIdAndNormalizedBranch_containsNormalizedBranch() {
        assertThat(conf.getPipelineStackName(), containsString(normalizedBranch));
    }

    @Test
    public void initCreateStackRole_projectIdAndNormalizedBranch_containsNormalizedBranch() {
        assertThat(conf.getCreateStackRoleName(), containsString(normalizedBranch));
    }


    @Test
    public void initCreateStackRole_projectIdAndNormalizedBranch_containsProjectId() {
        assertThat(conf.getCreateStackRoleName(), containsString(projectId));
    }


    @Test
    public void initPipelineRole_projectIdAndNormalizedBranch_containsProjectId() {
        assertThat(conf.getPipelineRoleName(), containsString(projectId));
    }



    @Test
    public void initPipelineRole_projectIdAndNormalizedBranch_containsNormalizedBranch() {
        assertThat(conf.getPipelineRoleName(), containsString(normalizedBranch));

    }


    @Test
    public void initPipelineRole_projectIdAndNormalizedBranch_noLongerThan64chars() {
        assertThat(conf.getPipelineRoleName().length(),
                is(not(greaterThan(PipelineStackConfiguration.MAX_ROLENAME_SIZE))));

    }


    @Test
    public void initStackRoleName_projectIdAndNormalizedBranch_noLongerThan64chars() {
        assertThat(conf.getCreateStackRoleName().length(),
                is(not(greaterThan(PipelineStackConfiguration.MAX_ROLENAME_SIZE))));

    }



    @Test
    public void initBucketName_projectIdAndNormalizedBranch_containsProjectId() {
        assertThat(conf.getBucketName(), containsString(projectId));

    }


    @Test
    public void initBucketName_projectIdAndNormalizedBranch_containsNormalizedBranch() {
        assertThat(conf.getBucketName(), containsString(normalizedBranch));

    }


    @Test
    public void initStackName_projectIdAndNormalizedBranch_compliesToAmazonRestrictions() {

        Matcher matcher = amazonPattern.matcher(conf.getPipelineStackName());
        assertThat(matcher.matches(), is(equalTo(true)));
        matcher = amazonPattern.matcher(conf.getPipelineConfiguration().getTestServiceStack());
        assertThat(matcher.matches(), is(equalTo(true)));
        matcher = amazonPattern.matcher(conf.getPipelineConfiguration().getFinalServiceStack());
        assertThat(matcher.matches(), is(equalTo(true)));

    }


}