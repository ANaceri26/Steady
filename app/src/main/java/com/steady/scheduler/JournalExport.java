package com.steady.scheduler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class JournalExport {
    public interface Photos { List<String> get(long id); }
    public static String escape(String s) {
        return s==null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    private static File photoFile(File root,String path) throws IOException {
        File file=new File(root,path).getCanonicalFile();
        if(!file.toPath().startsWith(root.getCanonicalFile().toPath()) || !file.isFile()) throw new IOException("Invalid or missing journal photo");
        return file;
    }
    public static void write(OutputStream output,List<String[]> entries,Photos photos,File files) throws IOException {
        StringBuilder html=new StringBuilder("<!doctype html><html lang='en'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>Steady Journal</title><style>body{font:16px/1.6 system-ui;max-width:820px;margin:32px auto;padding:0 20px;color:#18241c;background:#f6f8f6}article{background:white;border:1px solid #ccd7ce;border-radius:16px;padding:24px;margin:24px 0}p{white-space:pre-wrap;overflow-wrap:anywhere}img{max-width:100%;height:auto;border-radius:10px}h3{color:#456d28}</style><h1>Steady Journal</h1>");
        Map<String,String> paths=new LinkedHashMap<>();
        for(String[] row:entries){
            html.append("<article><h2>").append(escape(row[0])).append("</h2><p>Period: ").append(escape(row[1])).append("</p>");
            if(row.length>10) html.append("<p>Type: ").append(escape(row[8])).append(" · Group: ").append(escape(row[10])).append("</p><p>Project: ").append(escape(row[9])).append("</p>");
            String[] labels={"Progress / reflection","Obstacles & lessons","Next step"};
            for(int n=0;n<3;n++) if(row[n+2]!=null&&!row[n+2].isEmpty())html.append("<h3>").append(labels[n]).append("</h3><p>").append(escape(row[n+2])).append("</p>");
            for(String path:photos.get(Long.parseLong(row[6]))){
                photoFile(files,path);
                String safe=paths.get(path);
                if(safe==null){safe="photos/photo-"+(paths.size()+1)+".jpg";paths.put(path,safe);}
                html.append("<p><img src=\"").append(safe).append("\" alt=\"Journal photo\"></p>");
            }
            html.append("</article>");
        }
        html.append("</html>");
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            zip.putNextEntry(new ZipEntry("journal.html"));zip.write(html.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            byte[] buffer=new byte[8192];
            for(Map.Entry<String,String> path:paths.entrySet()){
                zip.putNextEntry(new ZipEntry(path.getValue()));
                try(InputStream in=new FileInputStream(photoFile(files,path.getKey()))){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}
                zip.closeEntry();
            }
        }
    }
}
