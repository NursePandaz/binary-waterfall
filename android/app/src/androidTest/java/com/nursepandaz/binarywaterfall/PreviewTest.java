package com.nursepandaz.binarywaterfall;

import android.app.Instrumentation;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.*;

@RunWith(AndroidJUnit4.class)
public final class PreviewTest {
    @Test
    public void testLaunchDemoPlaybackAndSettings() throws Throwable {
        Instrumentation instrumentation=getInstrumentation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"Try a demo").performClick());
            Button play=find(activity.getWindow().getDecorView(),"Play");
            for(int i=0;i<100;i++){instrumentation.waitForIdleSync();if(play.isEnabled())break;Thread.sleep(100);}
            assertTrue("Demo should prepare PCM playback",play.isEnabled());
            Thread.sleep(500);
            android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try(OutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),"preview.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            screenshot.recycle();
            getInstrumentation().runOnMainSync(()->{play.performClick();assertEquals("Pause",play.getText().toString());play.performClick();assertEquals("Play",play.getText().toString());find(activity.getWindow().getDecorView(),"Playback settings").performClick();});
        } finally { getInstrumentation().runOnMainSync(activity::finish); }
    }
    private static Button find(View view,String text) {
        if(view instanceof Button && ((Button)view).getText().toString().equals(text))return (Button)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){Button b=find(((ViewGroup)view).getChildAt(i),text);if(b!=null)return b;}
        return null;
    }
    @Test
    public void testWaveAndPixelMapping() throws Exception {
        File raw=File.createTempFile("raw-test",".bin",getInstrumentation().getTargetContext().getCacheDir());
        File wav=File.createTempFile("wave-test",".wav",getInstrumentation().getTargetContext().getCacheDir());
        try {
            try(OutputStream out=new FileOutputStream(raw)){for(int i=0;i<512;i++)out.write(i);}
            RawEngine.wave(raw,wav,32000,8,1);assertEquals(556,wav.length());
            try(RandomAccessFile in=new RandomAccessFile(raw,"r")){
                int[] p=RawEngine.pixels(in,4,4,"rgb",0.5,false,false);
                assertEquals(16,p.length);assertEquals(0xfff0f1f2,p[0]);
            }
        }finally{raw.delete();wav.delete();}
    }
}
