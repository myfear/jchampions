package org.acme.startup;

import io.quarkus.runtime.*;
import jakarta.enterprise.context.*;
import jakarta.enterprise.event.*;
import jakarta.inject.*;
import jakarta.transaction.*;
import org.acme.model.*;
import org.acme.model.dto.*;
import org.apache.camel.*;
import org.apache.camel.dataformat.bindy.csv.*;
import org.apache.camel.support.*;
import org.jboss.logging.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

@ApplicationScoped
public class ImportFromCSV
{
  @Inject
  ProducerTemplate producerTemplate;

  public void importFromCSV(String csvFilePath)
  {
    Path path = Paths.get(csvFilePath).toAbsolutePath();
    if (!Files.exists(path))
      throw new IllegalArgumentException("### CSV file not found: " + csvFilePath);
    try
    {
      Path targetDir = Paths.get("./csv-input");
      Files.createDirectories(targetDir);
      Path targetPath = targetDir.resolve("SelectedWithSchedule.csv");
      Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e)
    {
      throw new RuntimeException("### Error copying CSV file for processing", e);
    }
  }
}
