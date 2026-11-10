/* GPL-3.0-or-later; see the repository LICENSE. */
package com.nursepandaz.binarywaterfall;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed;
    private boolean busy, rendering, dragging;
    private File folder, raw, wav;
    private MediaPlayer player;
    private ImageView image;
    private TextView status, clock, filename;
    private Button open, demo, play, settings;
    private SeekBar seek;
    private int rate=32000, bits=8, channels=1, dimension=48, position;
    private String format="bgrx", displayName="No file selected";
    private boolean flip=true, prepared;
    private float volume=0.15f;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (player!=null && prepared && !dragging) {
                position=player.getCurrentPosition();
                seek.setProgress(position); clock.setText(time(position)+" / "+time(player.getDuration()));
                render();
                if (!player.isPlaying()) play.setText("Play");
            }
            ui.postDelayed(this,33);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        folder = new File(getCacheDir(),"session-"+System.nanoTime()); folder.mkdirs();
        raw=new File(folder,"input.bin"); wav=new File(folder,"audio.wav");
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setFitsSystemWindows(true);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(22),dp(16),dp(22),dp(24)); scroll.addView(box);
        if(Build.VERSION.SDK_INT>=30) scroll.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return insets;
        });
        TextView title=label("Binary Waterfall",28); title.setTextColor(Color.rgb(102,228,189)); box.addView(title);
        box.addView(label("ANDROID PREVIEW · RAW BYTES IN MOTION",11));
        filename=label(displayName,16); filename.setPadding(0,dp(16),0,dp(10)); box.addView(filename);
        image=new ImageView(this); image.setBackgroundColor(Color.BLACK); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setContentDescription("Raw file visualization");
        box.addView(image,new LinearLayout.LayoutParams(-1,dp(300)));
        clock=label("0:00 / 0:00",14); box.addView(clock);
        seek=new SeekBar(this); seek.setContentDescription("Playback position"); box.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s){dragging=true;}
            public void onProgressChanged(SeekBar s,int value,boolean user){if(user){position=value;render();clock.setText(time(value));}}
            public void onStopTrackingTouch(SeekBar s){dragging=false;if(prepared)player.seekTo(position);}
        });
        play=button("Play",box,()->toggle()); play.setEnabled(false);
        open=button("Open a file",box,()-> {
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            try { startActivityForResult(intent,10); } catch(android.content.ActivityNotFoundException e){error("No file picker is available.");}
        });
        demo=button("Try a demo",box,()->load(null,"Generated demo"));
        settings=button("Playback settings",box,()->settings());
        box.addView(label("Volume",14)); SeekBar loudness=new SeekBar(this); loudness.setContentDescription("Playback volume"); loudness.setMax(100); loudness.setProgress(15); box.addView(loudness);
        loudness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int value,boolean user){volume=value/100f;if(player!=null)player.setVolume(volume,volume);}
        });
        status=label("Choose any file, or try the demo. Files stay on this device. Preview limit: 256 MiB.",14); box.addView(status);
        box.addView(label("Based on Binary Waterfall by Ella Jameson · GPL v3\nPreview supports BGRX, RGB, grayscale and 8/16-bit PCM. Desktop export features are not included yet.",12));
        setContentView(scroll); ui.post(tick);
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(0xffd7e0e3);v.setPadding(0,dp(5),0,dp(5));return v;}
    private Button button(String text,LinearLayout box,Runnable run){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->run.run());box.addView(b);return b;}
    private static String time(int ms){return String.format(java.util.Locale.ROOT,"%d:%02d",ms/60000,ms/1000%60);}
    private void controls(boolean loading){busy=loading;open.setEnabled(!loading);demo.setEnabled(!loading);settings.setEnabled(!loading);play.setEnabled(!loading&&prepared);seek.setEnabled(!loading&&prepared);}
    private void release(){prepared=false;if(player!=null){player.release();player=null;}play.setText("Play");}
    private void error(String message){status.setText(message);controls(false);}
    private void toggle(){if(!prepared)return;if(player.isPlaying()){player.pause();play.setText("Play");}else{if(position>=player.getDuration())player.seekTo(0);player.start();play.setText("Pause");}}

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==10 && result==RESULT_OK && data!=null && data.getData()!=null){
            Uri uri=data.getData();String name="Selected file";
            try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null&&c.moveToFirst())name=c.getString(0);
            }catch(RuntimeException ignored){}
            load(uri,name);
        }
    }
    private void load(Uri uri,String name){
        if(busy)return;release();controls(true);status.setText("Preparing local preview…");position=0;seek.setProgress(0);
        worker.execute(()->{
            try{
                if(uri==null){try(OutputStream out=new FileOutputStream(raw)){for(int i=0;i<32000*8;i++)out.write((i*17 ^ i/128)&255);}}
                else try(InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IOException("Cannot open this file");RawEngine.copyBounded(in,raw);
                }
                RawEngine.wave(raw,wav,rate,bits,channels);
                ui.post(()->{if(!destroyed){displayName=name;filename.setText(name);prepare();}});
            }catch(Exception e){raw.delete();wav.delete();ui.post(()->{if(!destroyed)error("Could not open file: "+e.getMessage());});}
        });
    }
    private void prepare(){
        if(destroyed)return;
        try{
            MediaPlayer next=new MediaPlayer();player=next;
            next.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
            next.setDataSource(wav.getAbsolutePath());next.setVolume(volume,volume);
            next.setOnPreparedListener(p->{if(destroyed||p!=player)return;prepared=true;seek.setMax(p.getDuration());p.seekTo(Math.min(position,p.getDuration()));controls(false);status.setText(rate+" Hz · "+bits+" bit · "+(channels==1?"mono":"stereo")+" · "+raw.length()+" bytes");render();});
            next.setOnCompletionListener(p->{if(p==player){play.setText("Play");position=p.getDuration();seek.setProgress(position);render();}});
            next.setOnErrorListener((p,what,extra)->{if(p==player){release();error("This device could not decode the raw PCM preview. Try another audio format.");}return true;});
            next.prepareAsync();
        }catch(IOException|RuntimeException e){release();error("Could not prepare playback: "+e.getMessage());}
    }
    private void render(){
        if(destroyed||busy||rendering||!raw.isFile()||!prepared)return;rendering=true;
        final int size=dimension;final String mapping=format;final boolean invert=flip;
        final double fraction=seek.getMax()>0?(double)position/seek.getMax():0;
        worker.execute(()->{
            Bitmap bitmap=null;String problem=null;
            try(RandomAccessFile file=new RandomAccessFile(raw,"r")){
                int[] pixels=RawEngine.pixels(file,size,size,mapping,fraction,invert,true);
                bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
            }catch(Exception e){problem=e.getMessage();}
            final Bitmap result=bitmap;final String failure=problem;
            ui.post(()->{rendering=false;if(destroyed){if(result!=null)result.recycle();return;}
                if(result!=null)image.setImageDrawable(new android.graphics.drawable.BitmapDrawable(getResources(),result){{setFilterBitmap(false);}});
                else status.setText("Preview error: "+failure);
            });
        });
    }
    private Spinner options(LinearLayout box,String title,String[] values,int selected){
        box.addView(label(title,14));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(selected);box.addView(s);return s;
    }
    private void settings(){
        if(busy)return;if(prepared&&player.isPlaying()){player.pause();play.setText("Play");}
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(20),0,dp(20),0);box.setOrientation(LinearLayout.VERTICAL);
        int[] rates={8000,16000,22050,32000,44100,48000};int selected=0;for(int i=0;i<rates.length;i++)if(rates[i]==rate)selected=i;
        Spinner sr=options(box,"Sample rate",new String[]{"8000 Hz","16000 Hz","22050 Hz","32000 Hz","44100 Hz","48000 Hz"},selected);
        Spinner sb=options(box,"Sample size",new String[]{"8-bit unsigned","16-bit signed, little endian"},bits==8?0:1);
        Spinner sc=options(box,"Channels",new String[]{"Mono","Stereo"},channels-1);
        Spinner sf=options(box,"Pixel format",new String[]{"BGRX","RGB","Grayscale"},format.equals("bgrx")?0:format.equals("rgb")?1:2);
        Spinner sd=options(box,"Image size",new String[]{"32 × 32","48 × 48","64 × 64","128 × 128"},dimension==32?0:dimension==48?1:dimension==64?2:3);
        Switch sv=new Switch(this);sv.setText("Flip vertically");sv.setChecked(flip);box.addView(sv);
        new android.app.AlertDialog.Builder(this).setTitle("Playback settings").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{
            rate=rates[sr.getSelectedItemPosition()];bits=sb.getSelectedItemPosition()==0?8:16;channels=sc.getSelectedItemPosition()+1;
            format=new String[]{"bgrx","rgb","w"}[sf.getSelectedItemPosition()];dimension=new int[]{32,48,64,128}[sd.getSelectedItemPosition()];flip=sv.isChecked();position=0;
            if(raw.isFile()){
                release();controls(true);worker.execute(()->{try{RawEngine.wave(raw,wav,rate,bits,channels);ui.post(()->{if(!destroyed)prepare();});}catch(Exception e){ui.post(()->{if(!destroyed)error(e.getMessage());});}});
            }
        }).show();
    }
    @Override protected void onPause(){super.onPause();if(prepared&&player.isPlaying()){player.pause();play.setText("Play");}}
    @Override protected void onDestroy(){
        destroyed=true;ui.removeCallbacksAndMessages(null);release();
        worker.execute(()->{raw.delete();wav.delete();folder.delete();});worker.shutdown();super.onDestroy();
    }
}
