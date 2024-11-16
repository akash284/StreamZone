package com.stream.app.controllers;

import com.stream.app.AppConstants;
import com.stream.app.entities.Video;
import com.stream.app.payLoad.CustomMessage;
import com.stream.app.services.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// extra imports above
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@CrossOrigin("http://localhost:5173")
public class VideoController {

    private VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    // for uploading the video
    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title")String title,
            @RequestParam("description")String description
    ){

        if(file.isEmpty()){
            System.out.println("can't fetch the file from the postman");

        }
        Video video=new Video();
        video.setTitle(title);
        video.setDescription(description);
        video.setVideoId(UUID.randomUUID().toString());

        Video savedVideo = videoService.save(video, file);

        if(savedVideo!=null){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(video);
        }else{
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CustomMessage.builder()
                            .message("Video not uploaded Succesfully")
                            .success(false)
                            .build()
                    );
        }

    }

    // get all videos
    @GetMapping
    public List<Video> getAll(){
        return videoService.getAll();
    }


    //for streaming the video
    @GetMapping("/stream/{videoId}")
    public ResponseEntity<Resource> streamVideo(
            @PathVariable String videoId
    ){

        // get the video
        Video video = videoService.get(videoId);
        String contentType = video.getContentType();
        String filePath = video.getFilePath();

        // create the resource for that video stored in that filepath
        // resource represent karta h filepath pr rakhi hui file ko
        Resource resource=new FileSystemResource(filePath);

        if(contentType==null){
            contentType="application/octet-stream";
        }

        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }


    // stream video in chunks
    @GetMapping("/stream/range/{videoId}")
    public ResponseEntity<Resource> streamVideoRange(
            @PathVariable String videoId,
            @RequestHeader(value = "Range", required = false) String range){


        System.out.println("Range Header: " + range);


        Video video = videoService.get(videoId);
        String filePath = video.getFilePath();
        Path path = Paths.get(video.getFilePath());

      ///  System.out.println(path);
        Resource resource=new FileSystemResource(path);


        String contentType=video.getContentType();
        if(contentType==null){
            contentType="application/octet-stream";
        }

        //file ki length
        Long filelength=path.toFile().length();  // toFile gives the file object

        // pehle jesa hi code he bcz range null he
        if(range==null){
            return ResponseEntity
                    .ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        }

        // calculating starting and ending range
        long rangeStart;
        long rangeEnd;

        String[] stEndRanges = range.replace("bytes=", "").split("-");  // Range : byte= 100-200 -> 100-200 ->[100,200]
        rangeStart=Long.parseLong(stEndRanges[0]);

        rangeEnd=rangeStart+ AppConstants.CHUNK_SIZE-1;
        if(rangeEnd>=filelength){
            rangeEnd=filelength-1;
        }
//        if(stEndRanges.length>1){
//            rangeEnd=Long.parseLong(stEndRanges[1]);
//        } else{
//            rangeEnd=filelength-1;  // end range mention hi ni Range : byte=100
//        }
//
//        // ending range bahut jyada pass kardi  filelength se bhi jyada
//        if(rangeEnd>filelength-1){
//            rangeEnd=filelength-1;
//        }

        System.out.println("range Start : "+rangeStart);
        System.out.println("range End : "+rangeEnd);
        InputStream inputStream;

        try{

            inputStream= Files.newInputStream(path);
            inputStream.skip(rangeStart);  // jo starting head se aai he wahi se chahiye islie beggining se skip kardia

            long contentLength = rangeEnd - rangeStart + 1;


            byte[] data = new byte[(int) contentLength];
            int read = inputStream.read(data, 0, data.length);
            System.out.println("read(number of bytes) : " + read);

            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + filelength);
            headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
            headers.add("Pragma", "no-cache");
            headers.add("Expires", "0");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.setContentLength(contentLength);

            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new ByteArrayResource(data));
        }catch(IOException ex){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }




    }


    //serve hls playlist

    //master.m2u8 file

    @Value("${files.video.hlS}")
    private String HLS_DIR;


    // Video player is master file k according encode krke  segements ko load krlega

    //for streaming the hls segments
    @GetMapping("/{videoId}/master.m3u8")
    public ResponseEntity<Resource> serverMasterFile(
            @PathVariable String videoId
    ) {

//        creating path
        Path path = Paths.get(HLS_DIR, videoId, "master.m3u8");

        System.out.println(path);

        if (!Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl"
                )
                .body(resource);


    }

    //serve the segments
   // api ki help se segment ko get karpare he tabhi inhe serve karpayege
    @GetMapping("/{videoId}/{segment}.ts")
    public ResponseEntity<Resource> serveSegments(
            @PathVariable String videoId,
            @PathVariable String segment
    ) {

        // create path for segment
        Path path = Paths.get(HLS_DIR, videoId, segment + ".ts");
        if (!Files.exists(path)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Resource resource = new FileSystemResource(path);

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.CONTENT_TYPE, "video/mp2t"
                )
                .body(resource);

    }


}
