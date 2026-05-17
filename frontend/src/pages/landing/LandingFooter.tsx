// src/pages/landing/LandingFooter.tsx

import { Link } from "react-router-dom";

const links = [
  {
    heading: "Products",
    items: [
      { label: "Chequing Account", href: "#accounts" },
      { label: "Savings Account", href: "#accounts" },
      { label: "TFSA", href: "#accounts" },
    ],
  },
  {
    heading: "Features",
    items: [
      { label: "Interac e-Transfer", href: "#features" },
      { label: "Scheduled Transfers", href: "#features" },
      { label: "Monthly Statements", href: "#features" },
    ],
  },
  {
    heading: "Company",
    items: [
      { label: "About Us", href: "#" },
      { label: "Privacy Policy", href: "#" },
      { label: "Terms of Service", href: "#" },
    ],
  },
  {
    heading: "Support",
    items: [
      { label: "Help Centre", href: "#" },
      { label: "Contact Us", href: "#" },
      { label: "CDIC Disclosure", href: "#" },
    ],
  },
];

export function LandingFooter() {
  return (
    <footer className="bg-muted/40 border-t">
      <div className="max-w-6xl mx-auto px-6 py-14">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8 mb-12">
          {links.map(({ heading, items }) => (
            <div key={heading}>
              <h4 className="text-sm font-semibold mb-4">{heading}</h4>
              <ul className="space-y-2.5">
                {items.map(({ label, href }) => (
                  <li key={label}>
                    <a
                      href={href}
                      className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                    >
                      {label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="border-t pt-8 flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <div className="w-6 h-6 rounded-full bg-primary flex items-center justify-center">
              <span className="text-primary-foreground text-xs font-bold">M</span>
            </div>
            <span className="text-sm font-semibold">MapleBank</span>
          </div>
          <p className="text-xs text-muted-foreground text-center">
            © {new Date().getFullYear()} MapleBank. Deposits eligible for CDIC deposit insurance.
            MapleBank is a federally incorporated Canadian banking simulation.
          </p>
          <div className="flex gap-4">
            <Link to="/login" className="text-xs text-muted-foreground hover:text-foreground transition-colors">
              Sign in
            </Link>
            <Link to="/register" className="text-xs text-muted-foreground hover:text-foreground transition-colors">
              Open account
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
