package com.streamx.blueprints.index.processors.string;

import java.util.List;

public interface StringProcessor {

  List<String> process(List<String> input, String config);
}
