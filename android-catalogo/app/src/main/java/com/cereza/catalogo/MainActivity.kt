package com.cereza.catalogo

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val RAW = "https://raw.githubusercontent.com/cloudemax18/Proyecto-cereza/main/v1%20web/importar/"
private data class Part(val code:String,val name:String,val qty:Int,val note:String,val node:String,val photos:Int)
private fun csvLine(s:String):List<String> { val a=mutableListOf<String>(); val b=StringBuilder(); var q=false; var i=0
 while(i<s.length){val c=s[i];if(c=='"'){if(q&&i+1<s.length&&s[i+1]=='"'){b.append('"');i++}else q=!q}else if(c==','&&!q){a.add(b.toString());b.clear()}else b.append(c);i++};a.add(b.toString());return a }

private class PartsDb(c:android.content.Context):SQLiteOpenHelper(c,"cereza.db",null,1){
 override fun onCreate(d:SQLiteDatabase){d.execSQL("CREATE TABLE parts(code TEXT PRIMARY KEY,name TEXT,qty INTEGER,note TEXT,node TEXT,photos INTEGER)");d.execSQL("CREATE TABLE fav(code TEXT PRIMARY KEY)")}
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){}
 fun all():List<Part>{val a=mutableListOf<Part>();readableDatabase.rawQuery("SELECT * FROM parts ORDER BY name",null).use{c->while(c.moveToNext())a.add(Part(c.getString(0),c.getString(1),c.getInt(2),c.getString(3),c.getString(4),c.getInt(5)))};return a}
 fun import(s:String){val d=writableDatabase;d.beginTransaction();try{s.lineSequence().drop(1).filter{it.isNotBlank()}.forEach{l->val f=csvLine(l);if(f.size>=6){val v=ContentValues().apply{put("code",f[0]);put("name",f[1]);put("qty",f[2].toIntOrNull()?:0);put("note",f[3]);put("node",f[4]);put("photos",f[5].toIntOrNull()?:0)};d.insertWithOnConflict("parts",null,v,SQLiteDatabase.CONFLICT_REPLACE)}};d.setTransactionSuccessful()}finally{d.endTransaction()}}
 fun favs():Set<String>{val a=mutableSetOf<String>();readableDatabase.rawQuery("SELECT code FROM fav",null).use{c->while(c.moveToNext())a.add(c.getString(0))};return a}
 fun toggle(code:String){if(code in favs())writableDatabase.delete("fav","code=?",arrayOf(code))else writableDatabase.insert("fav",null,ContentValues().apply{put("code",code)})}
}
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val db=PartsDb(this);setContent{Catalog(db)}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Catalog(db:PartsDb){
 var parts by remember{mutableStateOf(db.all())};var favs by remember{mutableStateOf(db.favs())};var search by remember{mutableStateOf("")};var onlyFav by remember{mutableStateOf(false)};var loading by remember{mutableStateOf(false)};var detail by remember{mutableStateOf<Part?>(null)};var message by remember{mutableStateOf("")}
 LaunchedEffect(Unit){if(parts.isEmpty()){loading=true;try{val s=withContext(Dispatchers.IO){URL(RAW+"piezas.csv").readText()};withContext(Dispatchers.IO){db.import(s)};parts=db.all();message="Catálogo importado"}catch(e:Exception){message="No hay conexión. Comprueba internet e intenta actualizar."};loading=false}}
 val shown=parts.filter{(!onlyFav||it.code in favs)&&(search.isBlank()||it.code.contains(search,true)||it.name.contains(search,true)||it.node.contains(search,true))}
 val red=Color(0xFFD83A45);val ink=Color(0xFF18212F)
 MaterialTheme(colorScheme=lightColorScheme(primary=red,background=Color(0xFFF5F6F8),surface=Color.White)){
 Scaffold(containerColor=Color(0xFFF5F6F8),topBar={TopAppBar(title={Column{Text("CEREZA",fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("CATÁLOGO DE REPUESTOS",fontSize=10.sp,color=Color.Gray,letterSpacing=1.sp)}},actions={TextButton(onClick={loading=true}){Text("Actualizar")}})}){pad->
 Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
 if(detail!=null){val p=detail!!;TextButton(onClick={detail=null}){Text("← Volver")};PartPhoto(code=p.code,count=p.photos,contentDescription=p.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(24.dp)).background(Color.White));Spacer(Modifier.height(18.dp));Text(p.name,fontSize=24.sp,fontWeight=FontWeight.Bold,color=ink);Text(p.code,color=red,fontWeight=FontWeight.Bold);HorizontalDivider(Modifier.padding(vertical=12.dp));Text("Nodo: "+p.node);Text("Cantidad: "+p.qty);Text("Fotos indicadas: "+p.photos);if(p.note.isNotBlank())Text("Nota: "+p.note);Button(onClick={db.toggle(p.code);favs=db.favs()},modifier=Modifier.fillMaxWidth().padding(top=16.dp)){Text(if(p.code in favs)"♥ Quitar de favoritos" else "♡ Guardar en favoritos")}
 }else{
 Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ink).padding(20.dp)){Text("Encuentra la pieza correcta.",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Color.White);Text(parts.size.toString()+" referencias · búsqueda por código y nombre",color=Color.LightGray,fontSize=12.sp)}
 Spacer(Modifier.height(14.dp));OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("Nombre, código o nodo")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=!onlyFav,onClick={onlyFav=false},label={Text("Todas")});FilterChip(selected=onlyFav,onClick={onlyFav=true},label={Text("♥ Favoritas")})}
 if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(message.isNotBlank())Text(message,fontSize=11.sp,color=Color.Gray);Text(shown.size.toString()+" repuestos",fontWeight=FontWeight.SemiBold,color=Color.Gray,modifier=Modifier.padding(vertical=8.dp))
 LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(shown,key={it.code}){p->Card(Modifier.fillMaxWidth().clickable{detail=p},shape=RoundedCornerShape(18.dp)){PartPhoto(code=p.code,count=p.photos,contentDescription=p.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxWidth().height(140.dp).padding(8.dp));Column(Modifier.padding(start=12.dp,end=12.dp,bottom=14.dp)){Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp,minLines=2,maxLines=2);Text(p.code,color=red,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1);Text("Nodo "+p.node,color=Color.Gray,fontSize=10.sp,maxLines=1);Text("Stock: "+p.qty,fontSize=11.sp);TextButton(onClick={db.toggle(p.code);favs=db.favs()},contentPadding=PaddingValues(0.dp)){Icon(if(p.code in favs)Icons.Default.Favorite else Icons.Default.FavoriteBorder,null);Text(if(p.code in favs)"Guardado" else "Favorito")}}}}}
 }
 }}
 LaunchedEffect(loading){if(loading&&parts.isNotEmpty()){try{val s=withContext(Dispatchers.IO){URL(RAW+"piezas.csv").readText()};withContext(Dispatchers.IO){db.import(s)};parts=db.all();message="Catálogo actualizado"}catch(e:Exception){message="No se pudo actualizar"};loading=false}}
}


@Composable
private fun PartPhoto(
    code: String,
    count: Int,
    contentDescription: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    val candidates = remember(code, count) {
        (0..maxOf(count, 4)).map { index ->
            RAW + "fotos/" + code + (if (index == 0) "" else "_$index") + ".jpg"
        }
    }
    var candidateIndex by remember(code, count) { mutableIntStateOf(0) }
    AsyncImage(
        model = candidates[candidateIndex],
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = {
            if (candidateIndex < candidates.lastIndex) candidateIndex++
        }
    )
}
