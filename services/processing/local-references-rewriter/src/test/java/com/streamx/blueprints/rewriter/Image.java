package com.streamx.blueprints.rewriter;

class Image {

  final String originalPath;
  final String optimizedPath;

  Image(String originalPath) {
    this.originalPath = originalPath;
    this.optimizedPath = originalPath + "-optimized";
  }
}
