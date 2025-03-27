namespace PanicRecorder.Web;

public class AuthOptions
{
    public string SigningKey { get; set; } = "";
    public string AppKey { get; set; } = "";
}

public class S3Options
{
    public string AccessKeyId { get; set; } = "";
    public string SecretAccessKey { get; set; } = "";
    public string ServiceURL { get; set; } = "";
    public string BucketName { get; set; } = "";
}
