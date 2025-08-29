using System.Text;
using Amazon.S3;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;
using PanicRecorder.Web;
using PanicRecorder.Web.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Configuration
    .AddJsonFile("appsettings.json", optional: false, reloadOnChange: true)
    .AddJsonFile("appsettings.LocalUser.json", optional: true, reloadOnChange: true)
    .AddEnvironmentVariables();

var s3Config = builder.Configuration.GetSection("S3");
var authConfig = builder.Configuration.GetSection("Auth");

// Add services to the container.
builder.Services.AddSingleton<IAmazonS3>(_ => new AmazonS3Client(
    s3Config["AccessKeyId"],
    s3Config["SecretAccessKey"],
    new AmazonS3Config
    {
        ServiceURL = s3Config["ServiceURL"],
        ForcePathStyle = true // required for compatibility
    }));

builder.Services.AddSingleton<UploadService>();

builder.Services.Configure<AuthOptions>(authConfig);
builder.Services.Configure<S3Options>(s3Config);

builder.Services.AddControllers();


// Add JWT bearer authentication
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        var signingKey = authConfig["SigningKey"];
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(signingKey!));
        options.TokenValidationParameters = new Microsoft.IdentityModel.Tokens.TokenValidationParameters
        {
            ValidateAudience = false,
            ValidateIssuer = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = key,
        };
    });


// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();
