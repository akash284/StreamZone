package com.stream.app.services.Impl;

import com.stream.app.entities.Video;
import com.stream.app.repositories.VideoRepository;
import com.stream.app.services.VideoService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class VideoServiceImpl implements VideoService {

    @Value("${files.video}")
    String Dir;

    @Value("${files.video.hls}")
    String HLS_Dir;

    private VideoRepository videoRepository;

    public VideoServiceImpl(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @PostConstruct
    public void init(){
        File file=new File(Dir);

        // making hsl Directory
        File file1=new File(HLS_Dir);

        if(!file1.exists()){
            file1.mkdir();
            System.out.println("HSL video  folder created");
        }
        else{
            System.out.println("HSL video folder already exists");
        }


        if(!file.exists()){
            file.mkdir();
            System.out.println("video Folder created !!");
        }
        else{
            System.out.println("video Folder already exists !!");
        }

    }
    @Override
    public Video save(Video video, MultipartFile file) {

        try{

            String fileName=file.getOriginalFilename();
            if(fileName==null){
                System.out.println("Does not receive the file");
            }
            String contentType=file.getContentType();
            InputStream inputStream=file.getInputStream(); // to read the file


            // file path
            String cleanFileName = StringUtils.cleanPath(fileName);

            //folder path
            String cleanFolder = StringUtils.cleanPath(Dir);// to normalize the path and remove the redundant .,/,..

            // folder path with filename
            Path path = Paths.get(cleanFolder, cleanFileName);

          //  path.toString().replace("\\","/")
            System.out.println(contentType);
            System.out.println(path);

            // Copy file to the folder
            Files.copy(inputStream,path, StandardCopyOption.REPLACE_EXISTING);

            // video meta data
            video.setContentType(contentType);
//            video.setFilePath(path.toString());
            video.setFilePath(path.toString().replace("\\", "/"));


            Video savedVideo = videoRepository.save(video);
            //processing video
            processVideo(savedVideo.getVideoId());

            //delete actual video file and database entry  if exception

            // metadata save
            return savedVideo;



        }catch(IOException e){
            e.printStackTrace();
            return null;
        }


    }

    @Override
    public Video get(String videoId) {

        Video video = videoRepository.findById(videoId).orElseThrow(() -> new RuntimeException("VIdeo not found !"));

        return video;
    }

    @Override
    public Video getByTitle(String title) {
        return null;
    }

    @Override
    public List<Video> getAll() {
        return videoRepository.findAll();
    }

    @Override
    public String processVideo(String videoId) {

        Video video = this.get(videoId);
        String filePath = video.getFilePath();

        //path where to store data:
        Path videoPath = Paths.get(filePath);


//        String output360p = HSL_DIR + videoId + "/360p/";
//        String output720p = HSL_DIR + videoId + "/720p/";
//        String output1080p = HSL_DIR + videoId + "/1080p/";

        try {
//            Files.createDirectories(Paths.get(output360p));
//            Files.createDirectories(Paths.get(output720p));
//            Files.createDirectories(Paths.get(output1080p));

            // ffmpeg command
            Path outputPath = Paths.get(HLS_Dir, videoId);


            try {
                Files.createDirectories(outputPath);
                System.out.println("Directories created successfully: " + outputPath.toString());
            } catch (IOException e) {
                System.err.println("Failed to create directories: " + e.getMessage());
                throw new RuntimeException("Directory creation failed", e);
            }

            String ffmpegCmd = String.format(
                    "ffmpeg -i \"%s\" -c:v libx264 -c:a aac -strict -2 -f hls -hls_time 10 -hls_list_size 0 -hls_segment_filename \"%s/segment_%%3d.ts\"  \"%s/master.m3u8\" ",
                    videoPath, outputPath, outputPath
            );

//            StringBuilder ffmpegCmd = new StringBuilder();
//            ffmpegCmd.append("ffmpeg  -i ")
//                    .append(videoPath.toString())
//                    .append(" -c:v libx264 -c:a aac")
//                    .append(" ")
//                    .append("-map 0:v -map 0:a -s:v:0 640x360 -b:v:0 800k ")
//                    .append("-map 0:v -map 0:a -s:v:1 1280x720 -b:v:1 2800k ")
//                    .append("-map 0:v -map 0:a -s:v:2 1920x1080 -b:v:2 5000k ")
//                    .append("-var_stream_map \"v:0,a:0 v:1,a:0 v:2,a:0\" ")
//                    .append("-master_pl_name ").append(HSL_DIR).append(videoId).append("/master.m3u8 ")
//                    .append("-f hls -hls_time 10 -hls_list_size 0 ")
//                    .append("-hls_segment_filename \"").append(HSL_DIR).append(videoId).append("/v%v/fileSequence%d.ts\" ")
//                    .append("\"").append(HSL_DIR).append(videoId).append("/v%v/prog_index.m3u8\"");


            System.out.println(ffmpegCmd);
            //file this command
            ProcessBuilder processBuilder = new  ProcessBuilder("cmd.exe", "/c", ffmpegCmd);
            processBuilder.inheritIO();
            Process process = processBuilder.start();
            int exit = process.waitFor();
            System.out.println(exit);
            if (exit != 0) {
                throw new RuntimeException("video processing failed!!");
            }

            return videoId;

        } catch (IOException ex) {
            throw new RuntimeException("Video processing fail GOT THE EXCEPTION!!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
