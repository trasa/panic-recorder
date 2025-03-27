using Amazon.S3;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace PanicRecorder.Web.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class UploadController(IAmazonS3 s3Client, IOptions<S3Options> s3options) : ControllerBase
    {
        private IAmazonS3 S3Client { get; } = s3Client;
        private S3Options S3Options { get; } = s3options.Value;

        [HttpGet("presigned")]
        [Authorize]
        public async Task<IActionResult> GetPresignedUrl([FromQuery] string filename)
        {
            if (string.IsNullOrWhiteSpace(filename))
            {
                return BadRequest("filename is required");
            }
            var request = new Amazon.S3.Model.GetPreSignedUrlRequest
            {
                BucketName = S3Options.BucketName,
                Key = $"panic_chunks/{filename}",
                Verb = HttpVerb.PUT,
                Expires = DateTime.UtcNow.AddMinutes(15), // URL expires in 15 minutes
                ContentType = "video/MP2T"
            };
            var url = await S3Client.GetPreSignedURLAsync(request);
            return Ok(url);
        }
    }
}
