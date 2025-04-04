using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using PanicRecorder.Web.Auth;

namespace PanicRecorder.Web.Controllers;

[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private ILogger<AuthController> _logger;
    private readonly AuthOptions _authOptions;

    public AuthController(ILogger<AuthController> logger, IOptions<AuthOptions> authOptions)
    {
        _logger = logger;
        _authOptions = authOptions.Value;
    }

    private bool IsAppKeyValid() => Request.Headers.TryGetValue("Authorization", out var header) || header != $"Bearer {_authOptions.AppKey}";

    [HttpPost("token")]
    public IActionResult GenerateToken([FromBody] TokenRequest request)
    {
        if (!IsAppKeyValid())
        {
            _logger.LogWarning("Unauthorized key authentication attempt for {Username}", request.Username);
            return Unauthorized();
        }

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_authOptions.SigningKey));
        var creds = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            claims: [new Claim("sub", request.Username ?? "anonymous")],
            expires: DateTime.UtcNow.AddHours(1),
            signingCredentials: creds
        );
        _logger.LogInformation("Successful key authentication for {Username}", request.Username);
        var tokenString = new JwtSecurityTokenHandler().WriteToken(token);
        return Ok(new { token = tokenString });
    }
}
