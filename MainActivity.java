package com.example.projectorganizer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.AsyncTask;
import android.view.View;
import java.io.*;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    
    private EditText etGithubUrl;
    private EditText etAppName;
    private EditText etPackageName;
    private Button btnCreate;
    private TextView tvStatus;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etGithubUrl = findViewById(R.id.etGithubUrl);
        etAppName = findViewById(R.id.etAppName);
        etPackageName = findViewById(R.id.etPackageName);
        btnCreate = findViewById(R.id.btnCreate);
        tvStatus = findViewById(R.id.tvStatus);
        
        btnCreate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createProject();
            }
        });
    }
    
    private void createProject() {
        String githubUrl = etGithubUrl.getText().toString().trim();
        String appName = etAppName.getText().toString().trim();
        String packageName = etPackageName.getText().toString().trim();
        
        if (githubUrl.isEmpty() || appName.isEmpty() || packageName.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Remove .git if present
        if (githubUrl.endsWith(".git")) {
            githubUrl = githubUrl.substring(0, githubUrl.length() - 4);
        }
        
        String zipUrl = githubUrl + "/archive/refs/heads/main.zip";
        new ProjectSetupTask().execute(zipUrl, appName, packageName);
    }
    
    private class ProjectSetupTask extends AsyncTask<String, String, Boolean> {
        
        @Override
        protected void onPreExecute() {
            btnCreate.setEnabled(false);
            tvStatus.setText("Starting...");
        }
        
        @Override
        protected Boolean doInBackground(String... params) {
            String zipUrl = params[0];
            String appName = params[1];
            String packageName = params[2];
            
            try {
                File projectDir = new File(Environment.getExternalStorageDirectory(), appName);
                
                publishProgress("Downloading repository...");
                File zipFile = downloadFile(zipUrl);
                
                publishProgress("Extracting files...");
                extractZip(zipFile, projectDir);
                
                publishProgress("Creating Android structure...");
                createAndroidStructure(projectDir, appName, packageName);
                
                publishProgress("Creating Gradle files...");
                createGradleFiles(projectDir, appName, packageName);
                
                publishProgress("Creating AndroidManifest.xml...");
                createManifest(projectDir, appName, packageName);
                
                publishProgress("Organizing source files...");
                organizeSourceFiles(projectDir, packageName);
                
                zipFile.delete();
                return true;
                
            } catch (Exception e) {
                publishProgress("Error: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            tvStatus.setText(values[0]);
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            btnCreate.setEnabled(true);
            if (success) {
                tvStatus.setText("✓ Project created successfully!\nLocation: " + 
                    Environment.getExternalStorageDirectory() + "/" + etAppName.getText());
                Toast.makeText(MainActivity.this, "Project ready!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to create project", Toast.LENGTH_SHORT).show();
            }
        }
        
        private File downloadFile(String urlString) throws IOException {
            URL url = new URL(urlString);
            File tempFile = new File(getCacheDir(), "temp.zip");
            
            InputStream input = url.openStream();
            FileOutputStream output = new FileOutputStream(tempFile);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            
            output.close();
            input.close();
            return tempFile;
        }
        
        private void extractZip(File zipFile, File targetDir) throws IOException {
            targetDir.mkdirs();
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(file);
                    
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zis.closeEntry();
            }
            zis.close();
        }
        
        private void createAndroidStructure(File base, String appName, String pkg) {
            String[] dirs = {
                "app/src/main/java/" + pkg.replace(".", "/"),
                "app/src/main/res/layout",
                "app/src/main/res/values",
                "app/src/main/res/drawable",
                "app/src/main/res/mipmap-hdpi",
                "app/src/main/res/mipmap-mdpi",
                "app/src/main/res/mipmap-xhdpi",
                "app/src/main/res/mipmap-xxhdpi",
                "app/src/main/assets",
                "app/build",
                "app/libs",
                "gradle/wrapper"
            };
            
            for (String dir : dirs) {
                new File(base, dir).mkdirs();
            }
        }
        
        private void createGradleFiles(File base, String appName, String pkg) throws IOException {
            String rootGradle = "buildscript {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "    dependencies {\n" +
                "        classpath 'com.android.tools.build:gradle:7.4.0'\n" +
                "    }\n" +
                "}\n\n" +
                "allprojects {\n" +
                "    repositories {\n" +
                "        google()\n" +
                "        mavenCentral()\n" +
                "    }\n" +
                "}";
            
            writeFile(new File(base, "build.gradle"), rootGradle);
            
            String appGradle = "plugins {\n" +
                "    id 'com.android.application'\n" +
                "}\n\n" +
                "android {\n" +
                "    namespace '" + pkg + "'\n" +
                "    compileSdk 33\n\n" +
                "    defaultConfig {\n" +
                "        applicationId \"" + pkg + "\"\n" +
                "        minSdk 21\n" +
                "        targetSdk 33\n" +
                "        versionCode 1\n" +
                "        versionName \"1.0\"\n" +
                "    }\n\n" +
                "    buildTypes {\n" +
                "        release {\n" +
                "            minifyEnabled false\n" +
                "        }\n" +
                "    }\n" +
                "}\n\n" +
                "dependencies {\n" +
                "}";
            
            writeFile(new File(base, "app/build.gradle"), appGradle);
            
            writeFile(new File(base, "settings.gradle"), 
                "rootProject.name = '" + appName + "'\ninclude ':app'");
            
            writeFile(new File(base, "gradle.properties"),
                "org.gradle.jvmargs=-Xmx2048m\nandroid.useAndroidX=true");
        }
        
        private void createManifest(File base, String appName, String pkg) throws IOException {
            String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n\n" +
                "    <uses-permission android:name=\"android.permission.INTERNET\" />\n\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:icon=\"@mipmap/ic_launcher\"\n" +
                "        android:label=\"" + appName + "\"\n" +
                "        android:theme=\"@android:style/Theme.Material.Light\">\n" +
                "        <activity\n" +
                "            android:name=\".MainActivity\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n\n" +
                "</manifest>";
            
            writeFile(new File(base, "app/src/main/AndroidManifest.xml"), manifest);
        }
        
        private void organizeSourceFiles(File base, String pkg) {
            File srcDir = new File(base, "app/src/main/java/" + pkg.replace(".", "/"));
            moveJavaFiles(base, srcDir);
        }
        
        private void moveJavaFiles(File source, File dest) {
            if (source.isDirectory()) {
                File[] files = source.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            moveJavaFiles(file, dest);
                        } else if (file.getName().endsWith(".java")) {
                            try {
                                copyFile(file, new File(dest, file.getName()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        
        private void writeFile(File file, String content) throws IOException {
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);
            writer.write(content);
            writer.close();
        }
        
        private void copyFile(File src, File dst) throws IOException {
            FileInputStream fis = new FileInputStream(src);
            FileOutputStream fos = new FileOutputStream(dst);
            
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            
            fos.close();
            fis.close();
        }
    }
}
