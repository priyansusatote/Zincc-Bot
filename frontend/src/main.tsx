import "@fontsource-variable/inter/wght.css";
import { createRoot } from "react-dom/client";
import { Workbench } from "./Workbench";
import "./style.css";

const root = document.getElementById("root");
if (root) createRoot(root).render(<Workbench />);
