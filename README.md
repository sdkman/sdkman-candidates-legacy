#SDKMAN! Candidate Service

This is the main serverside component of [SDKMAN!](http://sdkman.io), the Software Development Kit Manager.

## Running the Server Locally

It is useful to run the server locally for development purposes. Working installations of MongoDB and vert.x are required to get going. Sdkman can be used to install vert.x, otherwise install it manually as described on the [install page](http://vertx.io/install.html).

    $ sdk install vertx

## MongoDB

The easiest way to get Mongo installed is using [Docker](http://docker.io). If you have Docker installed, simply enter the following in a terminal:

    $ docker run --rm -p 27017:27017 mongo:latest


If not using Docker, you can install MongoDB as described [here](http://www.mongodb.org/downloads).

After installing, the database needs to be primed with some data. The following lines may be run with `mongod` running as a separate process.

    $ mongo sdkman
    > db.application.insert({"alive" : "OK", "cliVersion" : "4.0.28" });
    > db.candidates.insert({"candidate" : "groovy", "default" : "2.4.6", "description" : "Groovy is a powerful, optionally typed and dynamic language, with static-typing and static compilation capabilities, for the Java platform aimed at multiplying developers' productivity thanks to a concise, familiar and easy to learn syntax. It integrates smoothly with any Java program, and immediately delivers to your application powerful features, including scripting capabilities, Domain-Specific Language authoring, runtime and compile-time meta-programming and functional programming.", "name" : "Groovy", "websiteUrl" : "http://www.groovy-lang.org/" });
    > db.candidates.insert({candidate:"groovy", default:"2.0.6"})
    > db.versions.insert({"candidate" : "groovy", "version" : "2.0.6", "url" : "https://bintray.com/artifact/download/groovy/maven/groovy-binary-2.0.6.zip" });
    > db.versions.insert({"candidate" : "groovy", "version" : "2.0.7", "url" : "https://bintray.com/artifact/download/groovy/maven/groovy-binary-2.0.7.zip" });


This will create:

* a new `sdkman` database,
* the `application` collection for app info and health check,
* the Groovy candidate in the `candidates` collection (defaulting to version 2.0.6),
* new versions 2.0.6 and 2.0.7 in the `versions` collection.

Add any other candidates that you might require.

Next, prepare the local SDKMAN environment by building and starting the server.

    $ ./gradlew clean assemble
    $ vertx run build/server/server.groovy

This will start the server on localhost:8080

### Customizing the Database Location

The database may be configured using environment variables. If non are found, it will assume sensible defaults for a local mongodb installation. The follow environment variables can be specified:

    SDKMAN_DB_ADDRESS="mongo-persistor"
    SDKMAN_DB_HOST="xxx.mongohq.com"
    SDKMAN_DB_PORT="1234"
    SDKMAN_DB_NAME="sdkman"
    SDKMAN_DB_PASSWORD="mypassword"
    SDKMAN_DB_USERNAME="myusername"
