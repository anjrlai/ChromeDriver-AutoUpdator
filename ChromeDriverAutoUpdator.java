import java.io.* ;
import java.util.* ;
import java.net.* ;
import java.util.zip.* ;

public class ChromeDriverAutoUpdator {

  public static void main(String[] args) throws Exception {

    loadProperties("config.properties");

    String ChromeVersion = getChromeVersion();
    String DriverVersion = getChromeDriverVersion();

    println("ChromeVersion:" + ChromeVersion);
    println("DriverVersion:" + DriverVersion);

    //compare the major version
    int ChromeVersionNumber = Integer.parseInt(ChromeVersion.substring(0, ChromeVersion.indexOf(".")));
    int DriverVersionNumber = Integer.parseInt(DriverVersion.substring(0, DriverVersion.indexOf(".")));

    if (DriverVersionNumber < ChromeVersionNumber) {
      String targetDriverVersion = get_latest_release(ChromeVersion.substring(0, ChromeVersion.indexOf(".")));
      targetDriverVersion = targetDriverVersion.substring(0, targetDriverVersion.length() - 1); //delete the CR/LF in the end
      println("Try to fetch... targetDriverVersion:" + targetDriverVersion);
      downloadFile(chromedriver_urlbase + targetDriverVersion + "/" + targetFileName + "", ".");
      Thread.sleep(3000);
      unzip(targetFileName, properties.getProperty("ChromeDriverDir"));
    }
    println("\r\n\r\n");
  }

  public static void loadProperties(String configFile) throws Exception {
    properties = new Properties();
    try {
      properties.load(new FileInputStream(configFile));

      targetFileName = properties.getProperty("targetFileName", "chromedriver_win32.zip");
      ProxyIP = properties.getProperty("ProxyIP");
      ProxyPort = Integer.parseInt(properties.getProperty("ProxyPort"));
      proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ProxyIP, ProxyPort));
    } catch(FileNotFoundException ex) {
      ex.printStackTrace();
      return;
    } catch(IOException ex) {
      ex.printStackTrace();
      return;
    }
  }

  public static String getChromeVersion() throws Exception {
    String data = "";
    try {
      String ChromeVersionCMD = properties.getProperty("ChromeVersionCMD", "WMIC datafile where name=\"C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome.exe\" get Version /value");
      data = execProcess(ChromeVersionCMD);

    } catch(Exception e) {
      e.printStackTrace();
    }
    return data.substring(data.indexOf("=") + 1, data.length());
  }

  public static String getChromeDriverVersion() throws IOException {
    String data = "";
    try {
      String ChromeDriverVersionCMD = properties.getProperty("ChromeDriverVersionCMD", "ChromeDriver.exe --version");
      String ChromeDriverDir = properties.getProperty("ChromeDriverDir");
      data = execProcess(ChromeDriverDir + ChromeDriverVersionCMD);

    } catch(Exception e) {
      e.printStackTrace();
    }
    return data.split(" ")[1];
  }

  public static String getInfoFromProcess(Process process) throws IOException {
    String Info = "";
    try {
      InputStreamReader inputstream = new InputStreamReader(process.getInputStream());
      reader = new BufferedReader(inputstream);
      String line = null;
      while ((line = reader.readLine()) != null) {
        if (line.length() > 0) {
          Info += line;
        }
      }
      inputstream.close();
    } catch(Exception e) {
      e.printStackTrace();
    }
    return Info;
  }

  public static String execProcess(String Command) throws IOException {
    try {
      rt = Runtime.getRuntime();
      Process process = rt.exec(Command);
      return getInfoFromProcess(process);

    } catch(Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  public static String get_latest_release(String VersionCode) throws Exception {
    url = new URL(chromedriver_urlbase + "LATEST_RELEASE_" + VersionCode);
    connection = (HttpURLConnection) url.openConnection(proxy);
    connection.connect();
    int responseCode = connection.getResponseCode();
    return readContent(connection);
  }

  public static String readContent(HttpURLConnection connection) throws Exception {
    String content = "";
    String line = "";
    BufferedReader in =new BufferedReader(new InputStreamReader(connection.getInputStream()));
    while ((line = in.readLine()) != null) {
      content += line + "\n";
    }
    return content;
  }

  public static void downloadFile(String fileURL, String saveDir) throws Exception {
    url = new URL(fileURL);
    connection = (HttpURLConnection) url.openConnection(proxy);
    int responseCode = connection.getResponseCode();

    if (responseCode == HttpURLConnection.HTTP_OK) {
      String fileName = "";
      String disposition = connection.getHeaderField("Content-Disposition");
      String contentType = connection.getContentType();
      int contentLength = connection.getContentLength();
      if (disposition != null) {
        int index = disposition.indexOf("filename=");
        if (index > 0) {
          fileName = disposition.substring(index + 10, disposition.length() - 1);
        }
      } else {
        fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
      }

      InputStream inputStream = connection.getInputStream();
      String saveFilePath = saveDir + File.separator + fileName;
      FileOutputStream outputStream = new FileOutputStream(saveFilePath);

      int bytesRead = -1;
      byte[] buffer = new byte[BUFFER_SIZE];
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
      outputStream.close();
      inputStream.close();
      println("File downloaded. [" + fileURL + "]");
    } else {
      println("No file to download. Server replied HTTP code: " + responseCode);
    }
    connection.disconnect();
  }

  public static void unzip(String fileZip, String destDir) throws Exception {
    println("Unzipping " + fileZip + " to " + destDir);
    byte[] buffer = new byte[1024];
    ZipInputStream zis = new ZipInputStream(new FileInputStream(fileZip));
    ZipEntry zipEntry = zis.getNextEntry();
    while (zipEntry != null) {
      File newFile = newFile(new File(destDir), zipEntry);
      if (zipEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }
        FileOutputStream fos = new FileOutputStream(newFile);
        int len;
        while ((len = zis.read(buffer)) > 0) {
          fos.write(buffer, 0, len);
        }
        fos.close();
      }
      zipEntry = zis.getNextEntry();
    }
    zis.closeEntry();
    zis.close();
    println("Unzip completed.");
  }

  public static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
    File destFile = new File(destinationDir, zipEntry.getName());
    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }
    return destFile;
  }

  public static void println(String Content) throws Exception {
    System.out.println(Content);
  }

  static Runtime rt;
  static Process process;
  static BufferedReader reader;
  static Proxy proxy;
  static URL url;
  static HttpURLConnection connection;
  static Properties properties;
  static String targetFileName;
  static String ProxyIP;
  static int ProxyPort;
  static String chromedriver_urlbase = "https://chromedriver.storage.googleapis.com/";
  private static final int BUFFER_SIZE = 4096;

}
