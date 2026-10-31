using System;
using System.Drawing;
using System.Security.Cryptography.X509Certificates;
using System.Windows.Forms;

namespace OpenVapeSigning
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new CertificateInstallerForm());
        }
    }

    public sealed class CertificateInstallerForm : Form
    {
        // Certificate bytes are embedded at build time by signing/build-installer.ps1.
        private static readonly byte[] CertificateBytes = Convert.FromBase64String("%CERT_BASE64%");

        private readonly Label statusLabel;
        private readonly Button installButton;
        private readonly Button closeButton;

        public CertificateInstallerForm()
        {
            Text = "OpenVape 证书安装工具";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(560, 300);
            BackColor = Color.FromArgb(24, 24, 28);
            ForeColor = Color.FromArgb(230, 230, 235);
            Font = new Font("Microsoft YaHei UI", 9.5f);

            var title = new Label
            {
                Text = "OpenVape 代码签名证书",
                Font = new Font("Microsoft YaHei UI", 15f, FontStyle.Bold),
                ForeColor = Color.FromArgb(120, 230, 190),
                AutoSize = true,
                Location = new Point(28, 24)
            };
            Controls.Add(title);

            string info = LoadCertificateInfo();
            var description = new Label
            {
                Text = "安装本证书后，Windows 会把 OpenVape 的签名识别为受信任发布者，\r\n" +
                       "从而不再提示“未知发布者”，SmartScreen 也不再拦截。\r\n\r\n" +
                       info,
                AutoSize = false,
                Size = new Size(504, 132),
                Location = new Point(28, 64)
            };
            Controls.Add(description);

            installButton = new Button
            {
                Text = "一键安装证书",
                Size = new Size(200, 44),
                Location = new Point(28, 214),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(46, 160, 120),
                ForeColor = Color.White,
                Font = new Font("Microsoft YaHei UI", 11f, FontStyle.Bold)
            };
            installButton.FlatAppearance.BorderSize = 0;
            installButton.Click += InstallButtonOnClick;
            Controls.Add(installButton);

            closeButton = new Button
            {
                Text = "退出",
                Size = new Size(110, 44),
                Location = new Point(422, 214),
                FlatStyle = FlatStyle.Flat,
                BackColor = Color.FromArgb(52, 52, 60),
                ForeColor = Color.FromArgb(225, 225, 230)
            };
            closeButton.FlatAppearance.BorderSize = 0;
            closeButton.Click += (sender, args) => Close();
            Controls.Add(closeButton);

            statusLabel = new Label
            {
                Text = "点击左侧按钮开始安装（需要管理员权限）。",
                AutoSize = false,
                Size = new Size(504, 40),
                Location = new Point(240, 224),
                ForeColor = Color.FromArgb(180, 180, 190)
            };
            Controls.Add(statusLabel);
        }

        private static X509Certificate2 LoadCertificate()
        {
            return new X509Certificate2(CertificateBytes);
        }

        private static string LoadCertificateInfo()
        {
            try
            {
                using (X509Certificate2 certificate = LoadCertificate())
                {
                    return "证书持有者：" + certificate.Subject + "\r\n" +
                           "有效期至：" + certificate.NotAfter.ToString("yyyy-MM-dd") + "\r\n" +
                           "指纹（SHA-1）：" + certificate.Thumbprint;
                }
            }
            catch (Exception)
            {
                return "内置证书数据无效。";
            }
        }

        private void InstallButtonOnClick(object sender, EventArgs e)
        {
            installButton.Enabled = false;
            statusLabel.ForeColor = Color.FromArgb(180, 180, 190);
            statusLabel.Text = "正在安装…";

            try
            {
                using (X509Certificate2 certificate = LoadCertificate())
                {
                    InstallInto(StoreName.Root, certificate);
                    InstallInto(StoreName.TrustedPublisher, certificate);
                    statusLabel.ForeColor = Color.FromArgb(120, 230, 190);
                    statusLabel.Text = "安装成功。重启浏览器/下载器后生效。";
                }
            }
            catch (Exception ex)
            {
                statusLabel.ForeColor = Color.FromArgb(240, 130, 130);
                statusLabel.Text = "安装失败：" + ex.Message;
            }
            finally
            {
                installButton.Enabled = true;
            }
        }

        private static void InstallInto(StoreName storeName, X509Certificate2 certificate)
        {
            using (var store = new X509Store(storeName, StoreLocation.LocalMachine))
            {
                store.Open(OpenFlags.ReadWrite);
                store.Add(certificate);
                store.Close();
            }
        }
    }
}
