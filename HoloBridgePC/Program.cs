using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Windows.Forms;

namespace HoloBridgePC;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new ReceiverForm());
    }
}

internal sealed class ReceiverForm : Form
{
    private readonly Label _status = new();
    private readonly Label _pinLabel = new();
    private readonly TextBox _content = new();
    private readonly NotifyIcon _tray = new();
    private readonly string _pin = Random.Shared.Next(100000, 999999).ToString();
    private TcpListener? _listener;
    private CancellationTokenSource? _cancel;

    public ReceiverForm()
    {
        Text = "STARK HoloBridge — PC Receiver";
        Width = 850; Height = 600; MinimumSize = new System.Drawing.Size(650, 420);
        BackColor = System.Drawing.Color.FromArgb(3, 10, 20);
        ForeColor = System.Drawing.Color.FromArgb(0, 243, 255);
        Font = new System.Drawing.Font("Segoe UI", 10);
        var title = new Label { Text = "STARK  /  HOLOBRIDGE", Dock = DockStyle.Top, Height = 58, TextAlign = System.Drawing.ContentAlignment.MiddleCenter, Font = new System.Drawing.Font(new System.Drawing.FontFamily("Segoe UI"), 18, System.Drawing.FontStyle.Regular), ForeColor = ForeColor };
        _pinLabel.Text = "PIN DE PAREAMENTO: " + _pin;
        _pinLabel.Dock = DockStyle.Top; _pinLabel.Height = 40; _pinLabel.TextAlign = System.Drawing.ContentAlignment.MiddleCenter;
        _status.Text = "Iniciando receptor local na porta 47821…";
        _status.Dock = DockStyle.Top; _status.Height = 32; _status.TextAlign = System.Drawing.ContentAlignment.MiddleCenter;
        _content.Multiline = true; _content.ReadOnly = true; _content.ScrollBars = ScrollBars.Vertical;
        _content.Dock = DockStyle.Fill; _content.BackColor = System.Drawing.Color.FromArgb(1, 4, 9);
        _content.ForeColor = System.Drawing.Color.FromArgb(180, 255, 255); _content.BorderStyle = BorderStyle.FixedSingle;
        Controls.Add(_content); Controls.Add(_status); Controls.Add(_pinLabel); Controls.Add(title);
        _tray.Text = "STARK HoloBridge"; _tray.Icon = System.Drawing.SystemIcons.Application;
        _tray.Visible = true; _tray.DoubleClick += (_, _) => { Show(); WindowState = FormWindowState.Normal; Activate(); };
        FormClosing += (_, e) =>
        {
            if (e.CloseReason == CloseReason.UserClosing && _tray.Visible)
            {
                e.Cancel = true; Hide();
            }
            else { _cancel?.Cancel(); _listener?.Stop(); _tray.Visible = false; _tray.Dispose(); }
        };
        Shown += (_, _) => StartReceiver();
    }

    private void StartReceiver()
    {
        try
        {
            _cancel = new CancellationTokenSource();
            _listener = new TcpListener(IPAddress.Any, 47821);
            _listener.Start();
            _status.Text = "RECEPTOR ATIVO • Porta 47821 • Mesmo Wi-Fi do celular";
            _content.Text = "Aguardando hologramas…\r\n\r\nNo HoloOS, informe o IP local deste PC e o PIN acima.\r\nSe o Windows Firewall perguntar, permita acesso em redes privadas.";
            _ = AcceptLoop(_cancel.Token);
        }
        catch (Exception ex) { _status.Text = "Falha ao iniciar: " + ex.Message; }
    }

    private async Task AcceptLoop(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _listener is not null)
        {
            TcpClient client;
            try { client = await _listener.AcceptTcpClientAsync(token); }
            catch { break; }
            _ = Task.Run(() => HandleClient(client, token), token);
        }
    }

    private async Task HandleClient(TcpClient client, CancellationToken token)
    {
        using (client)
        using (var stream = client.GetStream())
        {
            stream.ReadTimeout = 5000;
            var buffer = new byte[8192];
            using var memory = new MemoryStream();
            int headerEnd = -1;
            int contentLength = 0;
            while (memory.Length < 1024 * 1024)
            {
                int read;
                try { read = await stream.ReadAsync(buffer, token); } catch { return; }
                if (read == 0) return;
                memory.Write(buffer, 0, read);
                var bytes = memory.ToArray();
                headerEnd = FindHeaderEnd(bytes);
                if (headerEnd >= 0)
                {
                    var header = Encoding.ASCII.GetString(bytes, 0, headerEnd);
                    foreach (var line in header.Split("\r\n"))
                        if (line.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
                            int.TryParse(line[(line.IndexOf(':') + 1)..].Trim(), out contentLength);
                    if (memory.Length >= headerEnd + 4 + contentLength) break;
                }
            }
            var all = memory.ToArray();
            var responseStatus = "200 OK";
            var responseText = "OK";
            string display = "";
            try
            {
                if (headerEnd < 0) throw new InvalidDataException("Bad HTTP headers");
                var header = Encoding.ASCII.GetString(all, 0, headerEnd);
                if (!header.StartsWith("POST /holo ", StringComparison.Ordinal)) { responseStatus = "404 Not Found"; responseText = "Not found"; }
                else
                {
                    var body = Encoding.UTF8.GetString(all, headerEnd + 4, Math.Min(contentLength, all.Length - headerEnd - 4));
                    using var doc = JsonDocument.Parse(body);
                    var root = doc.RootElement;
                    if (!root.TryGetProperty("pin", out var pin) || pin.GetString() != _pin)
                    { responseStatus = "401 Unauthorized"; responseText = "Wrong PIN"; }
                    else
                    {
                        var type = Get(root, "type");
                        var title = Get(root, "title");
                        var payload = Get(root, "payload");
                        display = $"[{DateTime.Now:HH:mm:ss}] HOLOGRAMA RECEBIDO\r\nTipo: {type}\r\nTítulo: {title}\r\n\r\n{payload}\r\n\r\n────────────────────────────\r\n";
                        responseText = "Hologram received";
                    }
                }
            }
            catch { responseStatus = "400 Bad Request"; responseText = "Invalid request"; }
            var response = Encoding.UTF8.GetBytes($"HTTP/1.1 {responseStatus}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {Encoding.UTF8.GetByteCount(responseText)}\r\nConnection: close\r\nAccess-Control-Allow-Origin: *\r\n\r\n{responseText}");
            try { await stream.WriteAsync(response, token); } catch { }
            if (display.Length > 0)
                BeginInvoke(new Action(() => { _content.AppendText(display); _status.Text = "HOLOGRAMA RECEBIDO • " + DateTime.Now.ToString("HH:mm:ss"); Show(); WindowState = FormWindowState.Normal; Activate(); }));
        }
    }

    private static int FindHeaderEnd(byte[] b)
    {
        for (int i = 0; i < b.Length - 3; i++)
            if (b[i] == 13 && b[i + 1] == 10 && b[i + 2] == 13 && b[i + 3] == 10) return i;
        return -1;
    }

    private static string Get(JsonElement root, string name) =>
        root.TryGetProperty(name, out var value) ? (value.GetString() ?? "") : "";
}
