package com.rabbitmq.concourse;

import com.rabbitmq.concourse.CloudsmithResource.Input;
import com.rabbitmq.concourse.CloudsmithResource.Params;
import com.rabbitmq.concourse.CloudsmithResource.Source;
import com.rabbitmq.concourse.CloudsmithResource.Version;
import java.lang.reflect.Field;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

public class NativeImageFeature implements Feature {

  @Override
  public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
    Class<?>[] classes =
        new Class<?>[] {Input.class, Params.class, Source.class, Version.class, Package.class};
    RuntimeReflection.registerForReflectiveInstantiation(classes);
    for (Class<?> clazz : classes) {
      for (Field field : clazz.getDeclaredFields()) {
        RuntimeReflection.register(field);
      }
    }
  }
}
