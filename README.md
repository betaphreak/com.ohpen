# Vintra Test

See test-server.json for the Azure ARM template used to host the REST server

Application context is done with Spring.
Override the endpoint by setting -Dtest.endpoint=http://... and/or the default port,
 if other than 8000, with -Dtest.port=

Note that there is a undocumented HTTP 409 Conflict response from /auth/logout that is, most likely, a server bug.