/**
 * Copyright (C) 2014 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.policy.*;
import com.amazonaws.auth.policy.actions.EC2Actions;
import com.amazonaws.auth.policy.actions.ElasticBeanstalkActions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeAccountAttributesResult;
import com.amazonaws.services.elasticbeanstalk.AWSElasticBeanstalkClient;
import com.amazonaws.services.elasticbeanstalk.model.DescribeEnvironmentsResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import static com.amazonaws.auth.policy.Statement.Effect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Created by stas on 19/11/2014.
 */
@RunWith(BlockJUnit4ClassRunner.class)
public class GenerateIAMCredsForBeanstalkTest {
    private AmazonIdentityManagementClient iamClient;
    private static final String USER_NAME      = "iamc.bt.user";
    private static final String GROUP_NAME     = "iamc.eb.group";
    private static final String EB_POLICY_NAME = "iamc.eb.pol";
    private static final String EC2_POLICY_NAME = "iamc.ec2.pol";
    private AWSCredentials ebUserCreds;

    @Before
    public void before() {
        AWSCredentials awsCredentials = new AWSCredentials() {
            @Override public String getAWSAccessKeyId() {
                return System.getProperty("accessKeyShared");
            }

            @Override public String getAWSSecretKey() {
                return System.getProperty("accessKeySecret");
            }
        };

        // this is a full access account client, we use it only to setup a user account with limited access rights
        iamClient = new AmazonIdentityManagementClient(awsCredentials);

        // create a user
        try {
            CreateUserResult result = iamClient.createUser(new CreateUserRequest(USER_NAME));
        }
        catch( Exception ignore ) {}

        // create a group to use group policies
        try {
            iamClient.createGroup(new CreateGroupRequest(GROUP_NAME));
        }
        catch( Exception ignore ) {}
        Policy ebPolicy = new Policy(EB_POLICY_NAME).withStatements(new Statement(Effect.Allow).withActions(ElasticBeanstalkActions.AllElasticBeanstalkActions).withResources(new Resource("*")));
        PutGroupPolicyRequest groupPolicyRequest = new PutGroupPolicyRequest(GROUP_NAME, EB_POLICY_NAME, ebPolicy.toJson());
        iamClient.putGroupPolicy(groupPolicyRequest);
        iamClient.addUserToGroup(new AddUserToGroupRequest(GROUP_NAME, USER_NAME));

        // create test user credentials
        CreateAccessKeyResult createAccessKeyResult = iamClient.createAccessKey(new CreateAccessKeyRequest(USER_NAME));
        final AccessKey userAccessKey = createAccessKeyResult.getAccessKey();
        ebUserCreds = new AWSCredentials() {
            @Override public String getAWSAccessKeyId() {
                return userAccessKey.getAccessKeyId();
            }

            @Override public String getAWSSecretKey() {
                return userAccessKey.getSecretAccessKey();
            }
        };
        try {
            // wait for policies to settle
            Thread.sleep(10000L);
        }
        catch( InterruptedException ignore ) {
        }
    }

    @After
    public void after() {
        try {
            ListAccessKeysResult accessKeysResult = iamClient.listAccessKeys(new ListAccessKeysRequest().withUserName(USER_NAME));
            for( AccessKeyMetadata akmd : accessKeysResult.getAccessKeyMetadata()) {
                iamClient.deleteAccessKey(new DeleteAccessKeyRequest(USER_NAME, akmd.getAccessKeyId()));
            }
        } catch(Exception e) { e.printStackTrace(System.err); }
        try {
            iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest(GROUP_NAME, USER_NAME));
        } catch(Exception e) { e.printStackTrace(System.err); }
        try {
            iamClient.deleteUser(new DeleteUserRequest(USER_NAME));
        } catch(Exception e) { e.printStackTrace(System.err); }
        ListGroupPoliciesResult groupPoliciesResult = iamClient.listGroupPolicies(new ListGroupPoliciesRequest(GROUP_NAME));
        try {
            for( String policyName : groupPoliciesResult.getPolicyNames()) {
                iamClient.deleteGroupPolicy(new DeleteGroupPolicyRequest(GROUP_NAME, policyName));
            }
        } catch(Exception e) { e.printStackTrace(System.err); }
        try {
            iamClient.deleteGroup(new DeleteGroupRequest(GROUP_NAME));
        } catch(Exception e) { e.printStackTrace(System.err); }
    }

    @Test
    public void testEbUserCanAccessEb() {
        AWSElasticBeanstalkClient client = new AWSElasticBeanstalkClient(ebUserCreds);
        DescribeEnvironmentsResult result = client.describeEnvironments();
        assertNotNull(result);
    }

    @Test
    public void testEbUserCannotAccessEC2() {
        AmazonEC2Client client = new AmazonEC2Client(ebUserCreds);
        try {
            DescribeAccountAttributesResult result = client.describeAccountAttributes();
        } catch (AmazonServiceException e) {
            assertEquals(403, e.getStatusCode());
        }
    }

    @Test
    public void testEbUserAddEC2Access() {
        Policy ebPolicy = new Policy(EC2_POLICY_NAME).withStatements(new Statement(Effect.Allow).withActions(EC2Actions.AllEC2Actions).withResources(new Resource("*")));
        PutGroupPolicyRequest groupPolicyRequest = new PutGroupPolicyRequest(GROUP_NAME, EC2_POLICY_NAME, ebPolicy.toJson());
        iamClient.putGroupPolicy(groupPolicyRequest);
        try {
            // wait for policies to settle
            Thread.sleep(10000L);
        }
        catch( InterruptedException ignore ) {
        }

        AmazonEC2Client client = new AmazonEC2Client(ebUserCreds);
        DescribeAccountAttributesResult result = client.describeAccountAttributes();
        assertNotNull(result);

    }

}
