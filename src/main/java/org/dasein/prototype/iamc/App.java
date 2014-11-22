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

import com.amazonaws.services.identitymanagement.model.User;
import io.airlift.command.*;

import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Created by stas on 22/11/2014.
 */
public class App {

    public static void main(String ... args) {
        Cli.CliBuilder<Runnable> builder = Cli.<Runnable>builder("iamc")
                .withDescription("IAM command line utility")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, UsersList.class, UsersAdd.class, UsersDelete.class, UsersGrantAccess.class, UsersRevokeAccess.class);
        Cli<Runnable> parser = builder.build();
        Runnable command;
        try {
            command = parser.parse(args);
        } catch( Exception e ) {
            command = parser.parse("help");
        }
        command.run();
    }

    public static class UsersCommand implements Runnable
    {
        @Option( type = OptionType.GLOBAL, name = "-v", description = "Verbose mode" )
        public boolean verbose;

        public void run() {
            System.err.println("Command "+getClass().getSimpleName()+" not implemented.");
        }
    }

    @Command( name = "add", description = "Add a new user" )
    public static class UsersAdd extends UsersCommand {
        @Option( name = "-u", description = "User name", required = true)
        public String username;
        @Option( name = "-p", description = "User password", required = true)
        public String password;

        public void run() {
            User user = new AWS().addUser(username, password);
            System.out.printf("User (%s) created successfully\n", username);
            System.out.printf("This account's sign-in link: https://%s.signin.aws.amazon.com/console\n", AWS.getAccountNumber(user));
        }
    }

    @Command( name = "list", description = "List all users" )
    public static class UsersList extends UsersCommand {
        public void run() {
            List<User> users = new AWS().listUsers();
            SimpleDateFormat sdf = new SimpleDateFormat();
            System.out.println("-------------------------------------------------------");
            System.out.printf("User Id\t\t\tUser Name\tCreated\n");
            System.out.println("-------------------------------------------------------");
            for( User user : users ) {
                System.out.printf("%s\t%s\t%s\n", user.getUserId(), user.getUserName(), sdf.format(user.getCreateDate()));
            }
            System.out.println("-------------------------------------------------------");
            System.out.printf("Total %d users.\n", users.size());
            if( users.size() > 0 ) {
                System.out.printf("This account's sign-in link: https://%s.signin.aws.amazon.com/console\n", AWS.getAccountNumber(users.get(0)));
            }
        }
    }

    @Command( name = "delete", description = "Delete a user" )
    public static class UsersDelete extends UsersCommand {
        @Option( name = "-u", description = "User name" )
        public String username;

        public void run() {
            boolean result = new AWS().deleteUser(username);
            if( result ) {
                System.out.printf("User (%s) deleted successfully.\n", username);
            } else {
                System.err.printf("User (%s) not found.\n", username);
            }
        }
    }

    @Command( name = "grant", description = "Grant user access to a service: EB or EC2")
    public static class UsersGrantAccess extends UsersCommand {
        @Option( name = "-u", description = "User name" )
        public String username;

        @Option( name = "-s", description = "eb | ec2")
        public String service;

        public void run() {
            AWS.Service svc = null;
            if( "eb".equalsIgnoreCase(service) )
                svc = AWS.Service.ElasticBeanstalk;
            else if( "ec2".equalsIgnoreCase(service) )
                svc = AWS.Service.EC2;
            if( svc == null ) {
                System.err.printf("Service (%s) is invalid.", service);
                return;
            }
            new AWS().grantAccessToUser(username, svc);
            System.out.printf("User (%s) has been successfully granted access to service (%s).\n", username, service);
        }
    }

    @Command( name = "revoke", description = "Revoke user access to a service: EB or EC2")
    public static class UsersRevokeAccess extends UsersCommand {
        @Option( name = "-u", description = "User name" )
        public String username;

        @Option( name = "-s", description = "eb | ec2")
        public String service;

        public void run() {
            AWS.Service svc = null;
            if( "eb".equalsIgnoreCase(service) )
                svc = AWS.Service.ElasticBeanstalk;
            else if( "ec2".equalsIgnoreCase(service) )
                svc = AWS.Service.EC2;
            if( svc == null ) {
                System.err.printf("Service (%s) is invalid.", service);
                return;
            }
            new AWS().revokeAccessFromUser(username, svc);
            System.out.printf("User (%s) has been successfully revoked access to service (%s).\n", username, service);
        }
    }
}
