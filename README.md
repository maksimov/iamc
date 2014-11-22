iamc
====

About
-----
This is a proof of concept for support of dynamic AWS credentials with different levels of cloud services support.
Users are created with a login profile which they can use to sign-in to the account-specific sign-in page:
`https://<account-number>.signin.aws.amazon.com/console`

Build
-----
Build with `mvn clean install`

Usage
=====
Have a normal Dasein profile configured for AWS, or export two environment variables: `apiSharedKey` and `apiSecretKey`.

List users
----------
`mvn -q -P{dasein_aws_profile_name} exec:java -Dexec.args="list"`

Add user
--------
`mvn -q -P{dasein_aws_profile_name} exec:java -Dexec.args="add -u {user_name} -p {password}"`

Grant user access to a service
------------------------------
`mvn -q -P{dasein_aws_profile_name} exec:java -Dexec.args="grant -u {user_name} -s {eb|ec2}"`

Revoke user access to a service
------------------------------
`mvn -q -P{dasein_aws_profile_name} exec:java -Dexec.args="revoke -u {user_name} -s {eb|ec2}"`
