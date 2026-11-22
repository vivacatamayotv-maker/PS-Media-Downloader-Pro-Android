package ec.pepesimancas.psmediadownloader;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.*;
import com.yausername.youtubedl_android.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import java.io.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int BG=0xff0a1424, GOLD=0xffd5af58, LIGHT=0xfff0f3f8;
    private EditText link;
    private Spinner format,quality;
    private TextView details,status,percent;
    private ProgressBar progress;
    private Button download;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile String processId;
    private volatile boolean initialized=false;
    private volatile boolean cancelled=false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),dp(18),dp(18),dp(18));
        layout.setBackgroundColor(BG);
        scroll.addView(layout);
        setContentView(scroll);
        TextView heading=text(layout,"PS MEDIA DOWNLOADER PRO",22,GOLD,true);
        heading.setGravity(Gravity.CENTER);
        text(layout,"PEPE SIMANCAS CAPA · Android Beta",13,LIGHT,true).setGravity(Gravity.CENTER);
        text(layout,"ENLACE",13,LIGHT,true);
        link=new EditText(this);
        link.setHint("Pega un enlace compatible");
        link.setSingleLine(true);
        link.setTextColor(Color.WHITE);
        link.setHintTextColor(0xffa5b6c9);
        link.setPadding(dp(12),0,dp(12),0);
        link.setBackgroundColor(0xff1a3047);
        layout.addView(link,new LinearLayout.LayoutParams(-1,dp(52)));
        button(layout,"ANALIZAR",0xff31597d,()->analyze());
        details=text(layout,"Sin analizar",14,LIGHT,false);
        text(layout,"FORMATO",13,LIGHT,true);
        format=spinner(layout,new String[]{"Video MP4","Audio MP3"});
        text(layout,"CALIDAD DE VIDEO",13,LIGHT,true);
        quality=spinner(layout,new String[]{"Mejor MP4 directo","1080p","720p","480p","360p"});
        download=button(layout,"DESCARGAR AHORA",GOLD,()->download());
        button(layout,"DETENER DESCARGA",0xff6b3546,()->{
            cancelled=true;
            String id=processId;
            if(id!=null) YoutubeDL.getInstance().destroyProcessById(id);
            message("Deteniendo descarga…");
        });
        percent=text(layout,"0%",27,GOLD,true);percent.setGravity(Gravity.CENTER);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        layout.addView(progress,new LinearLayout.LayoutParams(-1,dp(18)));
        status=text(layout,"Preparando motor…",14,LIGHT,false);
        text(layout,"Archivos: Descargas/PS Media Downloader",12,0xffb9c6d5,false);
        text(layout,"Solo para contenido propio o cuya descarga esté autorizada.",11,0xffabb8c8,false);
        if(Intent.ACTION_SEND.equals(getIntent().getAction()) && "text/plain".equals(getIntent().getType())){
            String shared=getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if(shared!=null)link.setText(shared);
        }
        if(Build.VERSION.SDK_INT<=28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},123);
        worker.execute(()->{
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                initialized=true;message("Motor preparado. Pega un enlace.");
            } catch(Exception ex){message("Error al preparar motor: "+ex.getMessage());}
        });
    }
    private String url(){
        String v=link.getText().toString().trim();
        if(!(v.startsWith("https://")||v.startsWith("http://"))){message("Introduce un enlace válido.");return null;}
        return v;
    }
    private void analyze(){
        String u=url();if(u==null)return;
        if(!initialized){message("El motor todavía se está preparando.");return;}
        if(!busy.compareAndSet(false,true)){message("Hay otra operación activa.");return;}
        message("Analizando enlace…");
        worker.execute(()->{
            try{
                VideoInfo info=YoutubeDL.getInstance().getInfo(u);
                runOnUiThread(()->details.setText(info.getTitle()));
                message("Análisis completado: selecciona formato y calidad.");
            }catch(Exception ex){message("Error de análisis: "+ex.getMessage());}
            finally{busy.set(false);}
        });
    }
    private void download(){
        String u=url();if(u==null)return;
        if(!initialized){message("El motor todavía se está preparando.");return;}
        if(Build.VERSION.SDK_INT<=28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            message("Concede permiso de almacenamiento.");return;
        }
        if(!busy.compareAndSet(false,true)){message("Hay otra operación activa.");return;}
        int f=format.getSelectedItemPosition(),q=quality.getSelectedItemPosition();
        cancelled=false;download.setEnabled(false);progress.setProgress(0);percent.setText("0%");
        message("Preparando descarga…");
        worker.execute(()->{
            File base=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(base==null){message("Almacenamiento no disponible.");finish();return;}
            File temp=new File(base,"job_"+System.currentTimeMillis());temp.mkdirs();
            String id="ps_"+System.currentTimeMillis();processId=id;
            try{
                YoutubeDLRequest req=new YoutubeDLRequest(u);
                req.addOption("--no-playlist");req.addOption("--newline");
                req.addOption("--socket-timeout","15");req.addOption("--retries","3");
                req.addOption("-o",new File(temp,"%(title).100B [%(id)s].%(ext)s").getAbsolutePath());
                if(f==1){
                    req.addOption("-f","bestaudio/best");
                    req.addOption("-x");
                    req.addOption("--audio-format","mp3");
                } else {
                    String ceiling=q==0?"":("[height<="+(q==1?1080:q==2?720:q==3?480:360)+"]");
                    req.addOption("-f","best[ext=mp4]"+ceiling+"/bestvideo[ext=mp4]"+ceiling+"+bestaudio[ext=m4a]/best"+ceiling+"/best");
                    req.addOption("--merge-output-format","mp4");
                }
                YoutubeDL.getInstance().execute(req,(value,eta)->runOnUiThread(()->{
                    int p=Math.min(100,Math.max(0,Math.round(value)));
                    progress.setProgress(p);
                    percent.setText(String.format(Locale.US,"%.1f%%",value));
                    status.setText("Descargando · tiempo restante: "+eta+" s");
                }),id);
                if(cancelled){message("Descarga cancelada.");return;}
                File[] result=temp.listFiles((dir,name)->!name.endsWith(".part")&&!name.endsWith(".ytdl"));
                int count=0;
                if(result!=null)for(File file:result){
                    if(file.isFile()&&file.length()>0){
                        save(file,f==1?"audio/mpeg":"video/mp4");count++;
                    }
                }
                if(count==0)throw new IOException("No se encontró archivo final.");
                message("DESCARGA COMPLETADA · "+count+" archivo(s) guardado(s).");
                runOnUiThread(()->{progress.setProgress(100);percent.setText("100%");});
            }catch(Exception ex){message(cancelled?"Descarga cancelada.":"ERROR: "+ex.getMessage());}
            finally{processId=null;finish();}
        });
    }
    private void finish(){busy.set(false);runOnUiThread(()->download.setEnabled(true));}
    private void save(File file,String mime)throws IOException{
        if(Build.VERSION.SDK_INT>=29){
            ContentValues cv=new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME,file.getName());
            cv.put(MediaStore.Downloads.MIME_TYPE,mime);
            cv.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/PS Media Downloader");
            android.net.Uri target=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv);
            if(target==null)throw new IOException("No se pudo abrir carpeta Descargas.");
            try(InputStream input=new FileInputStream(file);OutputStream output=getContentResolver().openOutputStream(target)){
                if(output==null)throw new IOException("No se pudo crear archivo.");
                byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)output.write(b,0,n);
            }
        }else{
            File dest=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"PS Media Downloader");
            dest.mkdirs();
            File target=new File(dest,file.getName());
            int suffix=1;while(target.exists())target=new File(dest,(suffix++)+"_"+file.getName());
            try(InputStream input=new FileInputStream(file);OutputStream output=new FileOutputStream(target)){
                byte[] b=new byte[65536];int n;while((n=input.read(b))!=-1)output.write(b,0,n);
            }
        }
        file.delete();
    }
    private void message(String s){runOnUiThread(()->status.setText(s));}
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(0,dp(11),0,dp(9));if(bold)t.setTypeface(null,Typeface.BOLD);parent.addView(t);return t;
    }
    private Spinner spinner(LinearLayout parent,String[] values){
        Spinner spinner=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
        spinner.setAdapter(adapter);spinner.setBackgroundColor(0xffebf0f5);
        parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(52)));return spinner;
    }
    private Button button(LinearLayout parent,String value,int color,Runnable run){
        Button b=new Button(this);b.setText(value);b.setTextColor(color==GOLD?BG:Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        parent.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));b.setOnClickListener(v->run.run());return b;
    }
    @Override protected void onDestroy(){
        super.onDestroy();if(processId!=null)YoutubeDL.getInstance().destroyProcessById(processId);
        worker.shutdownNow();
    }
}
