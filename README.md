# Play Public Examples

A simple repo with a very simple Play server.  Generated using [Play Scala Seed](https://github.com/playframework/play-scala-seed.g8) 2.7.4, initially.

## Endpoints

From conf/routes:
```
GET     /                           controllers.HomeController.index
POST    /upload/tmp                 controllers.HomeController.tempFileUpload()
POST    /upload/stream              controllers.HomeController.fileStreamUpload()
POST    /upload/sink              controllers.HomeController.fileSinkUpload()
```

* `tempFileUpload` - Demonstrates a simple file upload endpoint.  Uses temporary files to cache the file data.
* `fileStreamUpload` - Demonstrates how one might upload a file stream.  Currently broken, due to [known issues](https://github.com/playframework/playframework/issues/7119)
* `fileSinkUpload` - Demonstrates how to stream data into your own sink.  Useful for cases where you might have wanted to stream the data.