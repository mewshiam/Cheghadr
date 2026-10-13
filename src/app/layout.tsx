import type { Metadata, Viewport } from "next";
import { Vazirmatn } from "next/font/google";
import "./globals.css";
import { Toaster } from "@/components/ui/toaster";
import { ThemeProvider } from "@/components/cheghadr/theme-provider";

const vazir = Vazirmatn({
  variable: "--font-vazir",
  subsets: ["arabic", "latin"],
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "چقدر؟ | Cheghadr? — Market Tracker",
  description:
    "Track Iranian and international currency, gold, coins, crypto and stock markets. پیگیری لحظه‌ای ارز، طلا، سکه، رمزارز و بورس",
  applicationName: "چقدر؟",
  keywords: [
    "چقدر",
    "قیمت لحظه‌ای",
    "دلار",
    "طلا",
    "سکه",
    "رمزارز",
    "بورس",
    "market tracker",
    "exchange rates",
  ],
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f7f9f8" },
    { media: "(prefers-color-scheme: dark)", color: "#0c1210" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fa" dir="rtl" suppressHydrationWarning>
      <body
        className={`${vazir.variable} antialiased bg-background text-foreground font-sans`}
      >
        <ThemeProvider>
          {children}
          <Toaster />
        </ThemeProvider>
      </body>
    </html>
  );
}
