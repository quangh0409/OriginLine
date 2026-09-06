import { personHandlers } from "./persons";
import { treeHandlers } from "./tree";
import { kinshipHandlers } from "./kinship";
import { eventHandlers } from "./events";
import { notificationHandlers } from "./notifications";
import { pushHandlers } from "./push";
import { graphqlHandlers } from "./graphql";

export const handlers = [
  ...personHandlers,
  ...treeHandlers,
  ...kinshipHandlers,
  ...eventHandlers,
  ...notificationHandlers,
  ...pushHandlers,
  ...graphqlHandlers,
];
