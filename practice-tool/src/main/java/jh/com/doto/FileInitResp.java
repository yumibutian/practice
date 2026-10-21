package jh.com.doto;

public record FileInitResp(Long fileId, boolean uploaded, boolean parsed, String uploadUrl) {

    public static FileInitResp needUpload(Long fileId) {
        return new FileInitResp(fileId, false, false, "files/" + fileId + "/upload");
    }

    public static FileInitResp reused(Long fileId, boolean parsed) {
        return new FileInitResp(fileId, true, parsed, null);
    }
}
