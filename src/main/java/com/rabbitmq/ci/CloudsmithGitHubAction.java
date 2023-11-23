/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package com.rabbitmq.ci;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CloudsmithGitHubAction {

  static final List<String> SOURCE_FIELDS =
      List.of(
          "username",
          "organization",
          "repository",
          "api_key",
          "name",
          "type",
          "distribution",
          "order_by");

  static final List<String> PARAMS_FIELDS =
      List.of(
          "delete",
          "do_delete",
          "republish",
          "globs",
          "tags",
          "local_path",
          "version",
          "version_filter",
          "keep_last_n",
          "keep_last_minor_patches");

  static final Function<String, String> FIELD_TO_ENVIRONMENT_VARIABLE =
      f -> "INPUT_" + f.toUpperCase();

  private static final Function<String, String> GITHUB_INPUTS_EXTRACTOR =
      field -> {
        String environmentVariableName = FIELD_TO_ENVIRONMENT_VARIABLE.apply(field);
        return System.getenv(environmentVariableName);
      };

  public static void main(String[] args) throws Exception {
    if (args.length == 1 && "test".equals(args[0])) {
      Utils.testSequence();
    }
    Input.Source source = mapSource(GITHUB_INPUTS_EXTRACTOR);
    Input.Params params = mapParams(GITHUB_INPUTS_EXTRACTOR);
    Input input = new Input().source(source).params(params);

    CloudsmithLogic logic = new CloudsmithLogic(input, new Log.GitHubActionsLog());
    String action = System.getenv("INPUT_ACTION");
    if ("download".equals(action)) {
      logic.in(params.localPath());
    } else if ("upload".equals(action)) {
      logic.upload(System.getProperty("user.dir"));
    } else if ("delete".equals(action)) {
      logic.delete();
    } else {
      throw new IllegalArgumentException("Action not supported: " + action);
    }
  }

  static Input.Source mapSource(Function<String, String> extractFunction) throws Exception {
    return map(buildInputs(SOURCE_FIELDS, extractFunction), Input.Source.class);
  }

  static Input.Params mapParams(Function<String, String> extractFunction) throws Exception {
    return map(buildInputs(PARAMS_FIELDS, extractFunction), Input.Params.class);
  }

  static Map<String, String> buildInputs(
      List<String> fields, Function<String, String> extractFunction) {
    return fields.stream()
        .map(k -> new Pair(k, extractFunction.apply(k)))
        .filter(p -> p.second != null && !p.second.isBlank())
        .collect(Collectors.toMap(Pair::first, Pair::second));
  }

  static <T> T map(Map<String, String> fields, Class<T> classToCreate)
      throws NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException,
          NoSuchFieldException {
    T obj = classToCreate.getDeclaredConstructor().newInstance();
    for (Map.Entry<String, String> entry : fields.entrySet()) {
      Field field = classToCreate.getDeclaredField(entry.getKey());
      field.setAccessible(true);
      if (Boolean.TYPE.equals(field.getType())) {
        field.set(obj, Boolean.parseBoolean(entry.getValue()));
      } else if (Integer.TYPE.equals(field.getType())) {
        field.set(obj, Integer.parseInt(entry.getValue()));
      } else {
        field.set(obj, entry.getValue());
      }
    }
    return obj;
  }

  private record Pair(String first, String second) {}
}
