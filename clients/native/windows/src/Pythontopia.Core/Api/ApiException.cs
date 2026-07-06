using System.Net;
using System.Text.Json;
using Pythontopia.Core.Json;

namespace Pythontopia.Core.Api;

/// <summary>The backend error envelope: <c>{code,message,details,traceId,timestamp}</c>.</summary>
public sealed record ApiErrorEnvelope(
    string? Code,
    string? Message,
    IReadOnlyList<string>? Details,
    string? TraceId,
    DateTimeOffset? Timestamp);

/// <summary>
/// Thrown for any non-success HTTP response. When the body is a backend
/// <see cref="ApiErrorEnvelope"/> the structured fields are parsed out; otherwise the
/// raw body (or status line) becomes the message. Callers can branch on
/// <see cref="IsUnauthorized"/> to trigger re-login on 401.
/// </summary>
public sealed class ApiException : Exception
{
    public HttpStatusCode StatusCode { get; }
    public string? Code { get; }
    public IReadOnlyList<string> Details { get; }
    public string? TraceId { get; }

    public bool IsUnauthorized => StatusCode == HttpStatusCode.Unauthorized;

    public ApiException(
        HttpStatusCode statusCode,
        string message,
        string? code = null,
        IReadOnlyList<string>? details = null,
        string? traceId = null,
        Exception? innerException = null)
        : base(message, innerException)
    {
        StatusCode = statusCode;
        Code = code;
        Details = details ?? Array.Empty<string>();
        TraceId = traceId;
    }

    public static ApiException FromResponse(HttpStatusCode statusCode, string? rawBody)
    {
        if (!string.IsNullOrWhiteSpace(rawBody))
        {
            try
            {
                var envelope = JsonSerializer.Deserialize<ApiErrorEnvelope>(rawBody, PytJson.Default);
                if (envelope is not null && (envelope.Message is not null || envelope.Code is not null))
                {
                    return new ApiException(
                        statusCode,
                        envelope.Message ?? envelope.Code ?? DefaultMessage(statusCode),
                        envelope.Code,
                        envelope.Details,
                        envelope.TraceId);
                }
            }
            catch (JsonException)
            {
                // Not a JSON envelope; fall back to the raw body below.
            }
        }

        var message = string.IsNullOrWhiteSpace(rawBody) ? DefaultMessage(statusCode) : rawBody!.Trim();
        return new ApiException(statusCode, message);
    }

    private static string DefaultMessage(HttpStatusCode statusCode) => $"HTTP {(int)statusCode} {statusCode}";

    public override string ToString()
    {
        var details = Details.Count > 0 ? $" [{string.Join("; ", Details)}]" : string.Empty;
        var trace = TraceId is null ? string.Empty : $" (traceId={TraceId})";
        return $"ApiException {(int)StatusCode} {StatusCode} {Code}: {Message}{details}{trace}";
    }
}
