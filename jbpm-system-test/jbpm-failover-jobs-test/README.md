ClusteredJobFailOverListener fails to remove data from cache
============================================================

This project is used as a reproducer to validate fix provided by [jbpm#2824](https://github.com/kiegroup/droolsjbpm-integration/pull/2824) 

[JBPM-10060] ClusteredJobFailOverListener fails to remove data from cache

## Building

For building this project locally, you firstly need to have the following tools installed locally:
- git client
- Java 1.8
- Maven
- docker (because of testcontainers makes use of it).

Once you cloned the repository locally all you need to do is execute the following Maven build:

```
mvn clean install
```

Once you cloned the repository locally all you need to do is execute the following Maven build (for cluster scenarios):

```
mvn clean install
```
