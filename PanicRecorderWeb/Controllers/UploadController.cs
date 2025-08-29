using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PanicRecorder.Web.Models;
using PanicRecorder.Web.Services;

namespace PanicRecorder.Web.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class UploadController(ILogger<UploadController> logger, UploadService uploadService) : ControllerBase
    {
        private ILogger<UploadController> Logger { get; } = logger;
        private UploadService UploadService { get; } = uploadService;

        [HttpGet("presigned")]
        [Authorize]
        public async Task<IActionResult> GetPresignedUrl([FromQuery] string filename)
        {
            if (string.IsNullOrWhiteSpace(filename))
            {
                Logger.LogWarning("Bad Request to get presigned URL (filename is required)");
                return BadRequest("filename is required");
            }
            Logger.LogInformation("Building PreSignedUrl for {Filename}", filename);
            var url = await UploadService.PresignSinglePut($"panic_chunks/{filename}", requiredHeaders: new Dictionary<string, string>
            {
                { "Content-Type", "video/MP2T" } // only include this if the client will also send this header
            });
            return Ok(url);
        }

        [HttpPost("presign")]
        [Authorize]
        public async Task<IActionResult> Presign([FromBody] PresignPutRequest request)
        {
            var url = await UploadService.PresignSinglePut(request.Key, requiredHeaders: null);
            return Ok(new PresignPutResponse(url, new Dictionary<string, string>()));
        }

        [HttpPost("multipart/start")]
        [Authorize]
        public async Task<IActionResult> StartMultipartUpload(StartMultipartRequest request)
        {
            var (uploadId, objectKey) = await UploadService.StartMultipartAsync(request.KeyHint);
            return Ok(new StartMultipartResponse(uploadId, objectKey));
        }

        [HttpPost("multipart/presign")]
        [Authorize]
        public async Task<IActionResult> PresignPart([FromBody] PresignPartRequest?  body, [FromQuery] string? uploadId, [FromQuery] int? partNumber, [FromQuery] string? objectKey)
        {
            var uid = body?.UploadId ?? uploadId;
            var pn = body?.PartNumber ?? partNumber;
            var key = body?.ObjectKey ?? objectKey;

            if (string.IsNullOrWhiteSpace(uid) || pn is null || pn <= 0 || string.IsNullOrWhiteSpace(key))
            {
                return BadRequest("uploadId, partNumber, objectKey are required");
            }
            var url = await UploadService.PresignPartUrl(key, uid, pn!.Value, headers: null);
            Logger.LogInformation("Building PreSignedUrl for {Url}: ", url);
            return Ok(new PresignPartResponse(url, new Dictionary<string, string>()));
        }

        [HttpPost("multipart/complete")]
        [Authorize]
        public async Task<IActionResult> CompleteMultipartUpload(CompleteMultipartRequest request)
        {
            await UploadService.CompleteMultipartAsync(request.ObjectKey, request.UploadId, request.Parts);
            return Ok(new
            {
                ok = true
            });
        }

        [HttpPost("multipart/abort")]
        [Authorize]
        public async Task<IActionResult> AbortMultipartUpload(CompleteMultipartRequest request)
        {
            await UploadService.AbortMultipartAsync(request.ObjectKey, request.UploadId);
            return Ok(new
            {
                ok = true
            });
        }
    }
}
