## More Kotlin Racing Ambiguous Coroutines

https://griffio.github.io/programming/2023/03/18/More-Kotlin-Racing-Ambiguous-Coroutines/

**Make staggered requests** to http resources with delayed responses and return the first coroutine to respond, cancelling the rest if already running

This is similar to [HappyEyeBalls](https://www.rfc-editor.org/rfc/rfc8305) where finding the quickest connection can be made with concurrency 

Use [Ktor http client](https://ktor.io/docs/create-client.html) with OkHttp engine or [Jdk async HttpClient](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html) using coroutine await extension

Send http requests to https://httpbin.org/ or run locally `docker run -p 80:80 kennethreitz/httpbin`

Requests will be run concurrently in random order with a staggered start until the first response

The next request will be run if the previous task fails or exceeds the delay (e.g. 800ms)

Each response is delayed (in seconds) before returning body from server

```
http://localhost/delay/7
800ms -> http://localhost/delay/9
         800ms -> http://localhost/delay/3
win <------------ 800ms -> http://localhost/delay/1
                           800ms -> http://localhost/delay/5
```

```
http://localhost/delay/7
http://localhost/delay/9
http://localhost/delay/3
http://localhost/delay/1
http://localhost/delay/5
{
  "args": {}, 
  "data": "", 
  "files": {}, 
  "form": {}, 
  "headers": {
    "Accept": "*/*", 
    "Accept-Charset": "UTF-8", 
    "Accept-Encoding": "gzip", 
    "Connection": "Keep-Alive", 
    "Host": "localhost", 
    "User-Agent": "Ktor client"
  }, 
  "origin": "192.168.65.1", 
  "url": "http://localhost/delay/1"
}

...in 3 second/s

```
