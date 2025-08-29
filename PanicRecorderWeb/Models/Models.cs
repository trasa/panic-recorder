namespace PanicRecorder.Web.Models;

public record PresignPutRequest(string Key);
public record PresignPutResponse(string Url, Dictionary<string,string> Headers);

public record StartMultipartRequest(string KeyHint);
public record StartMultipartResponse(string UploadId, string ObjectKey);

public record PresignPartRequest(string? UploadId, int? PartNumber, string? ObjectKey);
public record PresignPartResponse(string Url, Dictionary<string,string> Headers);

public record CompletedPart(int PartNumber, string ETag);
public record CompleteMultipartRequest(string UploadId, string ObjectKey, List<CompletedPart> Parts);
