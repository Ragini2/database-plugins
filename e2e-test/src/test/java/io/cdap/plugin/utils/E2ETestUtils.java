/*
 * Copyright © 2021 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.plugin.utils;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.cdap.e2e.utils.ConstantsUtil;
import io.cdap.e2e.utils.SeleniumDriver;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import stepsdesign.BeforeActions;

import java.io.IOException;
import java.util.Properties;

/**
 * E2ETestUtils contains the helper functions.
 */

public class E2ETestUtils {
  private static Properties pluginProperties = new Properties();
  private static Properties errorProperties = new Properties();
  private static final Logger logger = Logger.getLogger(E2ETestUtils.class);

  static {

    try {
      pluginProperties.load(E2ETestUtils.class.getResourceAsStream("/pluginParameters.properties"));
      errorProperties.load(E2ETestUtils.class.getResourceAsStream("/errorMessage.properties"));
    } catch (IOException e) {
      logger.error("Error while reading properties file" + e);
    }
  }

  public static String pluginProp(String property) {
    return pluginProperties.getProperty(property);
  }

  public static String errorProp(String property) {
    return errorProperties.getProperty(property);
  }

  public static void validateMandatoryPropertyError(String property) {
    String expectedErrorMessage = errorProp(E2ETestConstants.ERROR_MSG_MANDATORY)
            .replaceAll("PROPERTY", property);
    String actualErrorMessage = findPropertyErrorElement(property).getText();
    Assert.assertEquals(expectedErrorMessage, actualErrorMessage);
    String actualColor = E2ETestUtils.getErrorColor(E2ETestUtils.findPropertyErrorElement(property));
    String expectedColor = E2ETestUtils.errorProp(E2ETestConstants.ERROR_MSG_COLOR);
    Assert.assertEquals(expectedColor, actualColor);
  }

  public static WebElement findPropertyErrorElement(String property) {
    return SeleniumDriver.getDriver().findElement(
            By.xpath("//*[@data-cy='" + property + "']/following-sibling::div[@data-cy='property-row-error']"));
  }

  public static String getErrorColor(WebElement element) {
    String color = element.getCssValue(ConstantsUtil.COLOR);
    String[] hexValue = color.replace("rgba(", "").
            replace(")", "").split(",");
    int hexValue1 = Integer.parseInt(hexValue[0]);
    hexValue[1] = hexValue[1].trim();
    int hexValue2 = Integer.parseInt(hexValue[1]);
    hexValue[2] = hexValue[2].trim();
    int hexValue3 = Integer.parseInt(hexValue[2]);
    return String.format("#%02x%02x%02x", hexValue1, hexValue2, hexValue3);
  }

  public static void deleteBucket(String projectId, String bucketName) {
    try {
      Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
      Bucket bucket = storage.get(bucketName);
      StorageOptions.Builder optionsBuilder = StorageOptions.newBuilder();
      Iterable<Blob> blobs = storage.list(bucketName,
              Storage.BlobListOption.prefix("")).iterateAll();
      for (Blob blob : blobs) {
        blob.delete(Blob.BlobSourceOption.generationMatch());
      }

      storage.delete(bucketName,
              Storage.BucketSourceOption.userProject(projectId));
      bucket.delete();
      BeforeActions.scenario.write("Bucket " + bucket.getName() + " was deleted");
    } catch (Exception e) {
      BeforeActions.scenario.write("Bucket doesn't exist");
    }
  }

  public static String listObjects(String projectId, String bucketName) {
    String folderName = null;
    Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
    Page<Blob> blobs = storage.list(bucketName);
    for (Blob blob : blobs.iterateAll()) {
      if (blob.getName().contains("part")) {
        folderName = blob.getName();
        break;
      }
    }
    return folderName;
  }
}
