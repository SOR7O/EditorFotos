package com.photoeditorsdk.android.app

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import ly.img.android.pesdk.assets.filter.basic.FilterPackBasic
import ly.img.android.pesdk.assets.font.basic.FontPackBasic
import ly.img.android.pesdk.assets.frame.basic.FramePackBasic
import ly.img.android.pesdk.assets.overlay.basic.OverlayPackBasic
import ly.img.android.pesdk.assets.sticker.emoticons.StickerPackEmoticons
import ly.img.android.pesdk.assets.sticker.shapes.StickerPackShapes
import ly.img.android.pesdk.backend.model.constant.Directory
import ly.img.android.pesdk.backend.model.state.CameraSettings
import ly.img.android.pesdk.backend.model.state.EditorLoadSettings
import ly.img.android.pesdk.backend.model.state.EditorSaveSettings
import ly.img.android.pesdk.backend.model.state.manager.SettingsList
import ly.img.android.pesdk.ui.activity.CameraPreviewBuilder
import ly.img.android.pesdk.ui.activity.ImgLyIntent
import ly.img.android.pesdk.ui.activity.PhotoEditorBuilder
import ly.img.android.pesdk.ui.model.state.*
import ly.img.android.pesdk.ui.utils.PermissionRequest
import ly.img.android.serializer._3._0._0.PESDKFileWriter
import java.io.File
import java.io.IOException
import java.util.*

class MainActivity : Activity(), PermissionRequest.Response  {

    companion object {
        var REQUEST_RESULT = 1
        var GALLERY_RESULT = 2
    }
    private var filePath: Uri?=null
    private var storage: FirebaseStorage?=null
    internal var storageReference:StorageReference?=null

    private val PICK_IMAGE_REQUEST=1234
   fun onClick(p0: View?){
        if (p0===firebaseUpload){
            showFile()
        }
        else if(p0===firebaseUpload){
            uploadFile()
        }
    }
    private fun uploadFile(){
        if (filePath!=null){
            val progressDialog= ProgressDialog(this)
            progressDialog.setTitle("Cargando...")
            progressDialog.show()

            val imageRef=storageReference!!.child("image/"+UUID.randomUUID().toString())
            imageRef.putFile(filePath!!)
                    .addOnSuccessListener {
                        progressDialog.dismiss()
                        Toast.makeText(this,"Subiendo archivo",Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener(){
                        progressDialog.dismiss()
                        Toast.makeText(this,"No se pudo subir el archivo",Toast.LENGTH_SHORT).show()
                    }
                    .addOnProgressListener {takeSnapShot->
                      val progreso=100.0*takeSnapShot.bytesTransferred/takeSnapShot.totalByteCount
                        progressDialog.setMessage("Cargando"+progreso+"%...")
                    }

        }

    }
    private fun showFile(){
        val intent=Intent()
        intent.type="image/*"
        intent.action=Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent,"Selecciona la imagen"),PICK_IMAGE_REQUEST)
    }




    // Solicitud de permiso importante para Android 6.0
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        PermissionRequest.onRequestPermissionsResult(requestCode, permissions, grantResults)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }



    override fun permissionGranted() {}

    override fun permissionDenied() {
        // El permiso fue denegado
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage= FirebaseStorage.getInstance()
        storageReference=storage!!.reference

        firebaseUpload.setOnClickListener(){

        }

        //definimos variables de tipo Button para asignar los botones que hemos creado
        val startCamera = findViewById<Button>(R.id.startCamera)
        val openGallery = findViewById<Button>(R.id.openGallery)

        startCamera.setOnClickListener {
            openCamera()
        }
        openGallery.setOnClickListener {
            openSystemGalleryToSelectAnImage()
        }
    }

    private fun createSettingsList() = SettingsList().apply {
        //Paquetes assets
        getSettingsModel(UiConfigFilter::class.java).apply {
            setFilterList(FilterPackBasic.getFilterPack())
        }

        getSettingsModel(UiConfigText::class.java).apply {
            setFontList(FontPackBasic.getFontPack())
        }

        getSettingsModel(UiConfigFrame::class.java).apply {
            setFrameList(FramePackBasic.getFramePack())
        }

        getSettingsModel(UiConfigOverlay::class.java).apply {
            setOverlayList(OverlayPackBasic.getOverlayPack())
        }

        getSettingsModel(UiConfigSticker::class.java).apply {
            setStickerLists(
              StickerPackEmoticons.getStickerCategory(),
              StickerPackShapes.getStickerCategory()
            )
        }

        // Establecer configuraciones de exportación de imagen de cámara personalizadas
        getSettingsModel(CameraSettings::class.java)
          .setExportDir(Directory.DCIM, "IMAGENES")
          .setExportPrefix("camera_")

        // Establece la configuración de exportación de imágenes del editor personalizado
        getSettingsModel(EditorSaveSettings::class.java)
          .setExportDir(Directory.DCIM, "IMAGENES")
          .setExportPrefix("result_").savePolicy = EditorSaveSettings.SavePolicy.RETURN_ALWAYS_ONLY_OUTPUT

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == GALLERY_RESULT) {
           //Abrir el editor con uri
            //Imagen del sistema
            data?.data?.let { selectedImage ->
                openEditor(selectedImage)
            }

        } else if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_RESULT) {
            // Aqui el editor guarda la imagen
            val resultURI = data?.getParcelableExtra<Uri>(ImgLyIntent.RESULT_IMAGE_URI).also {
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(it))
            }
            val sourceURI = data?.getParcelableExtra<Uri>(ImgLyIntent.SOURCE_IMAGE_URI).also {
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).setData(it))
            }


            val lastState = data?.getParcelableExtra<SettingsList>(ImgLyIntent.SETTINGS_LIST)
            try {
                val pesdkFileWriter = PESDKFileWriter(lastState)
                pesdkFileWriter.writeJson(File(
                  Environment.getExternalStorageDirectory(),
                  "serialisationReadyToReadWithPESDKFileReader.json"
                ))
            } catch (e: IOException) {
                e.printStackTrace()
            }

        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == REQUEST_RESULT) {
            // El editor fue cancelado
            val sourceURI = data?.getParcelableExtra<Uri>(ImgLyIntent.SOURCE_IMAGE_URI)
            // TODO: Do something with the source...
        }
    }

    fun openSystemGalleryToSelectAnImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, GALLERY_RESULT)
        } else {
            Toast.makeText(
              this,
              "No hay una aplicacion de galeria instalada",
              Toast.LENGTH_LONG
            ).show()
        }
    }

    fun openEditor(Image: Uri) {
        val settingsList = createSettingsList()

        settingsList.getSettingsModel(EditorLoadSettings::class.java)
          .setImageSource(Image)

        PhotoEditorBuilder(this)
          .setSettingsList(settingsList)
          .startActivityForResult(this, REQUEST_RESULT)
    }

    private fun openCamera() {
        val settingsList = createSettingsList()

        CameraPreviewBuilder(this)
          .setSettingsList(settingsList)
          .startActivityForResult(this, REQUEST_RESULT)
    }

}
