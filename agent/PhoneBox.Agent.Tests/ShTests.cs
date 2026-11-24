using System.Text;
using PhoneBox.Agent;
using Xunit;

[CollectionDefinition("Process deadline", DisableParallelization = true)]
public sealed class ProcessDeadlineCollection { }

// Its deadline includes starting the shell and its child, so run the process fixture apart from other collections.
[Collection("Process deadline")]
public sealed class ShTests
{
    [Fact]
    public async Task OutputDeadlineAppliesAfterTheDirectProcessExits()
    {
        var path = Path.Combine(Path.GetTempPath(), "phonebox-sh-" + Guid.NewGuid().ToString("N"));
        var ready = path + ".ready";
        var release = path + ".release";
        var done = path + ".done";
        var (tool, args) = HoldingProcess(path);
        var run = Task.Run(() => Sh.RunWithTimeout(4000, tool, args));
        Exception? primary = null;
        try
        {
            await WaitForFile(ready);
            var winner = await Task.WhenAny(run, Task.Delay(TimeSpan.FromSeconds(8)));
            Assert.Same(run, winner);
            Assert.Null(await run);
            Assert.False(File.Exists(done));
        }
        catch (Exception ex)
        {
            primary = ex;
            throw;
        }
        finally
        {
            File.WriteAllText(release, "");
            try
            {
                await run.WaitAsync(TimeSpan.FromSeconds(24));
                if (File.Exists(ready)) await WaitForFile(done);
            }
            catch when (primary is not null) { }
            finally
            {
                File.Delete(ready);
                File.Delete(release);
                File.Delete(done);
            }
        }
    }

    private static (string Tool, string[] Args) HoldingProcess(string path)
    {
        if (OperatingSystem.IsWindows())
        {
            var escaped = path.Replace("'", "''");
            var script = $"$p = '{escaped}'; [IO.File]::WriteAllText($p + '.ready', ''); "
                + "$timer = [Diagnostics.Stopwatch]::StartNew(); "
                + "while (![IO.File]::Exists($p + '.release') -and $timer.Elapsed.TotalSeconds -lt 20) { Start-Sleep -Milliseconds 25 }; "
                + "[IO.File]::WriteAllText($p + '.done', '')";
            var encoded = Convert.ToBase64String(Encoding.Unicode.GetBytes(script));
            var powershell = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System),
                "WindowsPowerShell", "v1.0", "powershell.exe").Replace('\\', '/');
            // Avoid cmd.exe forwarding and double startup latency so the direct parent exits well within the 4 s deadline.
            var parent = "$info = New-Object Diagnostics.ProcessStartInfo; "
                + $"$info.FileName = '{powershell}'; $info.UseShellExecute = $false; "
                + $"$info.Arguments = '-NoLogo -NoProfile -NonInteractive -EncodedCommand {encoded}'; "
                + "[void][Diagnostics.Process]::Start($info)";
            return (powershell, ["-NoLogo", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                Convert.ToBase64String(Encoding.Unicode.GetBytes(parent))]);
        }

        const string unix = """
            (
                : > "$1.ready"
                for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
                    [ -f "$1.release" ] && break
                    sleep 1
                done
                : > "$1.done"
            ) &
            """;
        return ("/bin/sh", ["-c", unix, "phonebox-test", path]);
    }

    private static async Task WaitForFile(string path)
    {
        using var limit = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        while (!File.Exists(path))
            await Task.Delay(25, limit.Token);
    }
}
