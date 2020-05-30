# Vintra Test

See test-server.json for the Azure ARM template used to host the REST server

Application context is done with Spring.
Override the endpoint by setting -Dtest.endpoint=http://... and/or the default port,
 if other than 8000, with -Dtest.port=

Note that there is a undocumented HTTP 409 Conflict response from /auth/logout that is, most likely, a server bug.

I have a server-side problem submitting new contacts, requests are being redirected to a server called 'uvicorn' that's assumed to be on the same network as the container.

HTTP/1.1 307 Temporary Redirect [date: Sat, 30 May 2020 14:24:55 GMT, server: uvicorn, location: http://devlogicappportainer1.eastus2.azurecontainer.io:8000/api/v1/contacts/, transfer-encoding: chunked] ResponseEntityProxy{[Chunked: true]}