package com.streamx.blueprints.state.repository.rocksdb;

import java.util.Iterator;
import java.util.NoSuchElementException;
import org.rocksdb.RocksIterator;

public class RocksDbIteratorWrapper implements Iterator<String> {

  private final RocksIterator rocksIterator;
  private boolean isValid;

  public RocksDbIteratorWrapper(RocksIterator rocksIterator) {
    this.rocksIterator = rocksIterator;
    this.rocksIterator.seekToFirst();
    this.isValid = this.rocksIterator.isValid();
  }

  @Override
  public boolean hasNext() {
    return isValid;
  }

  @Override
  public String next() {
    if (!isValid) {
      throw new NoSuchElementException();
    }

    String key = new String(rocksIterator.key());
    rocksIterator.next();
    isValid = rocksIterator.isValid();
    return key;
  }

}