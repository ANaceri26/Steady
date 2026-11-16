package com.steady.scheduler;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.zip.*;

public class CoreTest {
    @Test public void batchHandlesBulletsBlankLinesAndUnicode(){
        assertEquals(Arrays.asList("Study","Gym","工程"),TaskBatch.parse("• Study\r\n\n2. Gym\n- 工程"));
    }
    @Test public void batchRejectsOversizedAndEmptyInput(){
        assertThrows(IllegalArgumentException.class,()->TaskBatch.parse("\n • \n"));
        assertThrows(IllegalArgumentException.class,()->TaskBatch.parse("x\n".repeat(26)));
        assertThrows(IllegalArgumentException.class,()->TaskBatch.parse("x".repeat(201)));
        assertThrows(IllegalArgumentException.class,()->TaskBatch.parse("x".repeat(30001)));
        assertThrows(IllegalArgumentException.class,()->TaskBatch.validateTime(-1));
        assertThrows(IllegalArgumentException.class,()->TaskBatch.validateTime(1440));
        TaskBatch.validateTime(0);TaskBatch.validateTime(1439);
    }
    @Test public void monthlyAndDaylightSavingSchedulesRemainValid(){
        assertTrue(Rules.due(LocalDate.of(2026,2,28),LocalDate.of(2026,1,31),Rules.MONTHLY));
        ZonedDateTime now=ZonedDateTime.of(2026,3,8,0,0,0,0,ZoneId.of("America/Toronto"));
        ZonedDateTime next=Rules.next(now.toLocalDate(),150,Rules.DAILY,now);
        assertEquals(3,next.getHour());assertTrue(next.toInstant().isAfter(now.toInstant()));
        assertNull(Rules.next(now.toLocalDate().minusDays(1),540,Rules.ONCE,now));
    }
    private String[] entry(){return new String[]{"<script>alert(1)</script>","2026-11-16","<img src=x onerror=alert(1)>","Lesson & note","Next","1","1",null,"Advice","Project <one>","Engineering"};}
    @Test public void exportEscapesUserContentAndIncludesMetadata() throws Exception {
        File root=Files.createTempDirectory("steady-export").toFile();
        File photo=new File(root,"photo.jpg");Files.write(photo.toPath(),new byte[]{1,2,3});
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        JournalExport.write(bytes,Collections.singletonList(entry()),id->Collections.singletonList("photo.jpg"),root);
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){
            assertEquals("journal.html",zip.getNextEntry().getName());String html=new String(zip.readAllBytes(),StandardCharsets.UTF_8);
            assertFalse(html.contains("<script>"));assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("Engineering"));assertTrue(html.contains("Project &lt;one&gt;"));
            assertEquals("photos/photo-1.jpg",zip.getNextEntry().getName());assertArrayEquals(new byte[]{1,2,3},zip.readAllBytes());assertNull(zip.getNextEntry());
        }finally{photo.delete();root.delete();}
    }
    @Test public void exportRejectsPhotoTraversal() throws Exception {
        File parent=Files.createTempDirectory("steady-export").toFile();File root=new File(parent,"photos");assertTrue(root.mkdir());File secret=new File(parent,"secret.txt");Files.write(secret.toPath(),"private".getBytes(StandardCharsets.UTF_8));
        try{assertThrows(IOException.class,()->JournalExport.write(new ByteArrayOutputStream(),Collections.singletonList(entry()),id->Collections.singletonList("../secret.txt"),root));}
        finally{secret.delete();root.delete();parent.delete();}
    }
}
