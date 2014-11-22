/**
 * Copyright (C) 2009-2014 Dell, Inc.
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
package org.dasein.prototype.iamc;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.policy.Action;
import com.amazonaws.auth.policy.Policy;
import com.amazonaws.auth.policy.Resource;
import com.amazonaws.auth.policy.Statement;
import com.amazonaws.auth.policy.actions.EC2Actions;
import com.amazonaws.auth.policy.actions.ElasticBeanstalkActions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.*;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Created by stas on 21/11/2014.
 */
public class AWS {

    public enum Service {
        ElasticBeanstalk,
        EC2
    }

    private AWSCredentials awsCredentials = new AWSCredentials() {
        @Override public String getAWSAccessKeyId() {
            return System.getProperty("accessKeyShared");
        }

        @Override public String getAWSSecretKey() {
            return System.getProperty("accessKeySecret");
        }
    };

    private AmazonIdentityManagementClient iamClient = new AmazonIdentityManagementClient(awsCredentials);
    private AmazonEC2Client                ec2Client = new AmazonEC2Client(awsCredentials);

    public static String getAccountNumber(@Nonnull User user) {
        return user.getArn().split(":")[4];
    }

    public List<User> listUsers() {
        return iamClient.listUsers().getUsers();
    }

    public User addUser( String username, String password ) {
        CreateUserResult createUserResult = iamClient.createUser(new CreateUserRequest(username));
        User user = createUserResult.getUser();
        iamClient.createLoginProfile(new CreateLoginProfileRequest(username, password));
        return user;
    }

    public boolean deleteUser( String username ) {
        try {
            for( String policy : iamClient.listUserPolicies(new ListUserPoliciesRequest(username)).getPolicyNames() ) {
                iamClient.deleteUserPolicy(new DeleteUserPolicyRequest(username, policy));
            }
        } catch( NoSuchEntityException ignore ) {}
        try {
            for( Group group : iamClient.listGroupsForUser(new ListGroupsForUserRequest(username)).getGroups() ) {
                iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest(group.getGroupName(), username));
            }
        } catch( NoSuchEntityException ignore ) {}
        try {
            iamClient.deleteLoginProfile(new DeleteLoginProfileRequest(username));
        } catch( Exception ignore ) {}
        try {
            iamClient.deleteUser(new DeleteUserRequest(username));
            return true;
        } catch( NoSuchEntityException e ) {
        } catch( DeleteConflictException e ) {
            e.printStackTrace(System.err);
        }
        return false;
    }

    public boolean grantAccessToUser(String username, Service service) {
        String entityName;
        Action action;
        switch( service ) {
            case ElasticBeanstalk:
                entityName = "iamc-eb";
                action = ElasticBeanstalkActions.AllElasticBeanstalkActions;
                break;
            case EC2:
                entityName = "iamc-ec2";
                action = EC2Actions.AllEC2Actions;
                break;
            default:
                return false;
        }
        try {
            iamClient.getGroup(new GetGroupRequest(entityName));
        } catch( NoSuchEntityException e ) {
            iamClient.createGroup(new CreateGroupRequest(entityName));
        }
        Policy policy = new Policy(entityName).withStatements(new Statement(Statement.Effect.Allow).withActions(action).withResources(new Resource("*")));
        iamClient.putGroupPolicy(new PutGroupPolicyRequest(entityName, entityName, policy.toJson()));
        iamClient.addUserToGroup(new AddUserToGroupRequest(entityName, username));
        return true;
    }

    public boolean revokeAccessFromUser( String username, Service service ) {
        String entityName;
        switch( service ) {
            case ElasticBeanstalk:
                entityName = "iamc-eb";
                break;
            case EC2:
                entityName = "iamc-ec2";
                break;
            default:
                return false;
        }
        try {
            iamClient.getGroup(new GetGroupRequest(entityName));
        } catch( NoSuchEntityException e ) {
            // no group, no access to revoke
        }
        try {
            iamClient.removeUserFromGroup(new RemoveUserFromGroupRequest(entityName, username));
        } catch( Exception ignore ) {}
        return true;
    }

}
