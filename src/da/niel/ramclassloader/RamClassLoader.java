package da.niel.ramclassloader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RamClassLoader extends ClassLoader {

    private final byte[] jarBytes;
    private final Set<String> names;

    private final Map<String, byte[]> classes;

    private boolean hasMain;
    private String mainName;

    public RamClassLoader(byte[] jarBytes) throws IOException {
        this.classes = new HashMap<>();
        this.jarBytes = jarBytes;
        this.names = this.loadNames(jarBytes);
    }

    public void insertClass(String name, byte[] jarBytes) {
        if(name.endsWith(".class"))name = name.substring(0, name.length()-6);
        this.classes.put(name, jarBytes);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name, false);
    }

    private Set<String> loadNames(byte[] jarBytes) throws IOException {
        Set<String> set = new HashSet<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().contains("META-INF")) {
                    if(entry.getName().contains("MANIFEST.MF")){
                        String[] ss = new String(this.streamToArray(zipInputStream), StandardCharsets.UTF_8).split("\n");
                        int i = 0;
                        for(; i < ss.length; ++i){
                            if(ss[i].startsWith("Main-Class: ")){
                                this.hasMain = true;
                                break;
                            }
                        }
                        this.mainName = ss[i].substring(12).trim();
                    }


                    continue;
                }
                set.add(entry.getName());
                if (entry.getName().endsWith(".class")) {
                    String s = entry.getName().replace('/', '.');
                    if(s.endsWith(".class"))s = s.substring(0, s.length()-6);
                    this.insertClass(s, this.streamToArray(zipInputStream));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }


    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] code = this.classes.get(name);
        if (code != null) return defineClass(name, code, 0, code.length);
        return super.findClass(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (!this.names.contains(name)) {
            return null;
        }
        boolean b = false;
        ZipInputStream zipInputStream = null;
        try {
            zipInputStream = new ZipInputStream(new ByteArrayInputStream(this.jarBytes));
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    b = true;
                    return zipInputStream;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (zipInputStream != null && !b) {
                try {
                    zipInputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    public String getMainName() {
        return this.mainName;
    }

    public boolean hasMain() {
        return this.hasMain;
    }

    private byte[] streamToArray(InputStream stream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[4096];
            int bytesRead;

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return outputStream.toByteArray();
    }
}
