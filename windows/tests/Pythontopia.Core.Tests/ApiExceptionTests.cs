using System.Net;
using Pythontopia.Core.Api;
using Xunit;

namespace Pythontopia.Core.Tests;

public class ApiExceptionTests
{
    [Fact]
    public void FromResponse_ParsesJsonEnvelope()
    {
        const string body = """
        {"code":"VALIDATION_ERROR","message":"用户名已存在","details":["dup-username"],"traceId":"t-1"}
        """;

        var ex = ApiException.FromResponse(HttpStatusCode.BadRequest, body);

        Assert.Equal(HttpStatusCode.BadRequest, ex.StatusCode);
        Assert.Equal("VALIDATION_ERROR", ex.Code);
        Assert.Equal("用户名已存在", ex.Message);
        Assert.Equal(new[] { "dup-username" }, ex.Details);
        Assert.Equal("t-1", ex.TraceId);
    }

    [Fact]
    public void FromResponse_NonJsonBody_FallsBackToRawBody()
    {
        var ex = ApiException.FromResponse(HttpStatusCode.InternalServerError, "boom");

        Assert.Equal("boom", ex.Message);
        Assert.Null(ex.Code);
        Assert.Empty(ex.Details);
    }

    [Fact]
    public void FromResponse_EmptyBody_UsesStatusDefault()
    {
        var ex = ApiException.FromResponse(HttpStatusCode.NotFound, "");

        Assert.Contains("404", ex.Message);
        Assert.Empty(ex.Details);
    }

    [Fact]
    public void FromResponse_Null401_IsUnauthorized()
    {
        var ex = ApiException.FromResponse(HttpStatusCode.Unauthorized, null);

        Assert.True(ex.IsUnauthorized);
    }
}
