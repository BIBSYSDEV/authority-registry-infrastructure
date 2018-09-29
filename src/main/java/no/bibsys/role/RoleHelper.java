package no.bibsys.role;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.Role;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import no.bibsys.IOUtils;

public class RoleHelper {

  AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();

  IOUtils ioUtils=new IOUtils();



  public void deleteRole(String rolename){
     getRole(rolename)
        .map(this::deleteInlinePolicies)
         .map(this::detachPolicies)
        .map(role->new DeleteRoleRequest().withRoleName(role.getRoleName()))
         .ifPresent(deleteRequest->iam.deleteRole(deleteRequest));
  }


  public Optional<Role> getRole(String rolename){
    GetRoleRequest getRole = new GetRoleRequest().withRoleName(rolename);
    try{
      GetRoleResult result = iam.getRole(getRole);
      return Optional.of(result.getRole());
    }
    catch(NoSuchEntityException e){
      return Optional.empty();
    }


  }

  public  Role createRole(String roleName) throws IOException {
    AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
    deleteRole(roleName);
    createEmptyRole(iam, roleName);
    attachPolicies(iam, roleName);
    PutRolePolicyRequest inlinePolicy = bucketAccessInlinePolicy(roleName);
    iam.putRolePolicy(inlinePolicy);

    Role role = getRole(roleName).get();
    return role;

  }


  private PutRolePolicyRequest bucketAccessInlinePolicy(String roleName) throws IOException {

    String inlinePolicyName=roleName+"_bucket_access";
    String accessToBucket = ioUtils
        .resourceAsString(Paths.get("policies", "accessToBucket.json"));

    return new PutRolePolicyRequest()
        .withPolicyDocument(accessToBucket)
        .withRoleName(roleName)
        .withPolicyName(inlinePolicyName);
  }


  private void attachPolicies(AmazonIdentityManagement iam, String roleName) {
    List<String> policies = listDefaultAmazonPolicies();
    policies.forEach(p -> attacheRolePolicy(iam, roleName, p));
  }

  private void createEmptyRole(AmazonIdentityManagement iam, String roleName) throws IOException {
    CreateRoleRequest createRoleRequest = new CreateRoleRequest();
    createRoleRequest.setRoleName(roleName);
    String assumeRolePolicyDocument = ioUtils
        .removeMultipleWhiteSpaces(ioUtils.resourceAsString(
            Paths.get("policies", "assumeRolePolicy.json")));
    createRoleRequest.setAssumeRolePolicyDocument(assumeRolePolicyDocument);
    iam.createRole(createRoleRequest);

  }

  private Role detachPolicies(Role role) {
    ListAttachedRolePoliciesResult result = iam
        .listAttachedRolePolicies(
            new ListAttachedRolePoliciesRequest().withRoleName(role.getRoleName()));

    Stream<DetachRolePolicyRequest> detatchRequests = result.getAttachedPolicies().stream()
        .map(policy -> new DetachRolePolicyRequest()
            .withPolicyArn(policy.getPolicyArn())
            .withRoleName(role.getRoleName())
        );

    detatchRequests.forEach(iam::detachRolePolicy);
    return role;
  }

  private Role deleteInlinePolicies(Role role) {
    List<String> inlinePolicies = listInlinePolicies(role);
    inlinePolicies.stream().forEach(policy->iam.deleteRolePolicy(new DeleteRolePolicyRequest()
    .withPolicyName(policy).withRoleName(role.getRoleName())));
    return role;
  }

  private List<String> listInlinePolicies(Role role) {
    ListRolePoliciesRequest inlinePolicies=new ListRolePoliciesRequest();
    inlinePolicies.setRoleName(role.getRoleName());
    ListRolePoliciesResult result = iam
        .listRolePolicies(inlinePolicies);
    return result.getPolicyNames();

  }



  private List<String> listDefaultAmazonPolicies() {
    List<String> policies = new ArrayList<>();
    policies.add("arn:aws:iam::aws:policy/AWSCodePipelineFullAccess");
    policies.add("arn:aws:iam::aws:policy/AWSCodeCommitFullAccess");
    policies.add("arn:aws:iam::aws:policy/AWSCodeBuildAdminAccess");
//        policies.add("arn:aws:iam::aws:policy/AmazonS3FullAccess");
    return policies;
  }



  private void attacheRolePolicy(AmazonIdentityManagement iam, String roleName,
      String policyArn) {
    AttachRolePolicyRequest attachRoleRequest = new AttachRolePolicyRequest();
    attachRoleRequest.setPolicyArn(policyArn);

    attachRoleRequest.setRoleName(roleName);
    iam.attachRolePolicy(attachRoleRequest);
  }


  private Optional<GetRoleResult> getRole(GetRoleRequest getRole) {
    AmazonIdentityManagement iam = AmazonIdentityManagementClientBuilder.defaultClient();
    try {
      GetRoleResult role = iam.getRole(getRole);
      return Optional.of(role);
    } catch (NoSuchEntityException e) {
      return Optional.empty();

    }

  }



}
