using Amazon.S3;
using Amazon.S3.Model;
using Microsoft.Extensions.Options;
using PanicRecorder.Web.Models;

namespace PanicRecorder.Web.Services;

public class UploadService
{
    private readonly IAmazonS3 _s3;
    private readonly string _bucket;
    private readonly TimeSpan _expiry;

    public UploadService(IAmazonS3 s3, IOptions<S3Options> options)
    {
        _s3 = s3;
        _bucket = options.Value.BucketName;
        _expiry = TimeSpan.FromMinutes(30); // TODO move to option object
    }

    public async Task<string> PresignSinglePut(string key, IDictionary<string,string>? requiredHeaders = null)
    {
        // If you want to force Content-Type or x-amz-* headers, include them in the request; otherwise omit.
        var req = new GetPreSignedUrlRequest
        {
            BucketName = _bucket,
            Key = key,
            Verb = HttpVerb.PUT,
            Expires = DateTime.UtcNow.Add(_expiry)
        };

        // Example: to force a specific content type in the signature, uncomment:
        // req.Headers["Content-Type"] = "video/MP2T";

        if (requiredHeaders != null)
            foreach (var kv in requiredHeaders)
                req.Headers[kv.Key] = kv.Value;

        return await _s3.GetPreSignedURLAsync(req);
    }

    public async Task<(string UploadId, string ObjectKey)> StartMultipartAsync(string keyHint)
    {
        // You can transform keyHint to your final key here if you want uniqueness.
        var key = keyHint;
        var init = new InitiateMultipartUploadRequest
        {
            BucketName = _bucket,
            Key = key
            // ContentType = "video/MP2T" // include only if the client will also send this header
        };
        var resp = await _s3.InitiateMultipartUploadAsync(init);
        return (resp.UploadId, key);
    }


    public async Task<string> PresignPartUrl(string objectKey, string uploadId, int partNumber, IDictionary<string,string>? headers = null)
    {
        var req = new GetPreSignedUrlRequest
        {
            BucketName = _bucket,
            Key = objectKey,
            Verb = HttpVerb.PUT,
            Expires = DateTime.UtcNow.Add(_expiry),
            UploadId = uploadId,
            PartNumber = partNumber,
        };
        if (headers != null)
            foreach (var kv in headers)
                req.Headers[kv.Key] = kv.Value;

        return await _s3.GetPreSignedURLAsync(req);
    }

    public async Task CompleteMultipartAsync(string objectKey, string uploadId, IEnumerable<CompletedPart> parts)
    {
        var comp = new CompleteMultipartUploadRequest
        {
            BucketName = _bucket,
            Key = objectKey,
            UploadId = uploadId
        };
        var completedParts = parts as CompletedPart[] ?? parts.ToArray();
        comp.AddPartETags(completedParts.OrderBy(p => p.PartNumber).Select(p => new PartETag(p.PartNumber,p.ETag)));
        foreach (var p in completedParts.OrderBy(p => p.PartNumber))
        {
            comp.AddPartETags(new PartETag(p.PartNumber, p.ETag));
        }

        await _s3.CompleteMultipartUploadAsync(comp);
    }


    public async Task AbortMultipartAsync(string objectKey, string uploadId)
    {
        await _s3.AbortMultipartUploadAsync(new AbortMultipartUploadRequest
        {
            BucketName = _bucket,
            Key = objectKey,
            UploadId = uploadId
        });
    }
}
